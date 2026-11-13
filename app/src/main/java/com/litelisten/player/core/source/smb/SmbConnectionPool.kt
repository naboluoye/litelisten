package com.litelisten.player.core.source.smb

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import com.litelisten.player.R
import com.litelisten.player.core.common.LanAddress
import com.litelisten.player.core.common.NonLanAddressException
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.SocketTimeoutException
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SmbOpenFile(val handle: SmbFile, val size: Long)

/** smbj 对象非线程安全，所有共享操作串行；凭据只留内存，不写日志。 */
@Singleton
class SmbConnectionPool @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Credentials(val config: SourceConfig, val username: String?, val password: String?)

    private data class Entry(
        val client: SMBClient,
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
    )

    private val configurations = ConcurrentHashMap<String, Credentials>()
    private val entries = ConcurrentHashMap<String, Entry>()
    private val lock = Any()

    // 与 Media3 的 C.LENGTH_UNSET 同值；连接池不依赖播放器层。
    private companion object {
        const val UNKNOWN_LENGTH = -1L
        const val MAX_SHARES = 8
    }

    fun configure(config: SourceConfig, username: String?, password: String?) {
        configurations[config.id] = Credentials(config, username, password)
        close(config.id)
    }

    fun forget(sourceId: String) {
        configurations.remove(sourceId)
        close(sourceId)
    }

    fun configOf(sourceId: String): SourceConfig? = configurations[sourceId]?.config

    /**
     * 尽力列出共享名。smbj 0.14 没有 NetShareEnum 的高级封装，
     * 用一次登录会话逐个尝试常见共享名，能连上的即为可用共享。
     * 用户从「发现的设备」进来只有 IP，不给他这一步就只能猜共享名。
     */
    fun listShares(sourceId: String): List<String> = synchronized(lock) {
        val saved = configurations[sourceId]
            ?: throw SourceError.Protocol("MissingHost")
        val config = saved.config
        val host = config.host?.takeIf { it.isNotBlank() }
            ?: throw SourceError.Protocol("MissingHost")
        if (!LanAddress.isPrivateOrResolvableToPrivate(host)) {
            val shortName = !host.contains('.') && !host.contains(':')
            throw if (shortName) SourceError.Protocol("HOST_UNRESOLVED")
            else NonLanAddressException(host)
        }
        val client = SMBClient(smbConfig())
        val found = LinkedHashSet<String>()
        try {
            val connection = client.connect(host, config.port ?: 445)
            val session = connection.authenticate(authentication(saved, config))
            for (name in context.resources.getStringArray(R.array.common_smb_shares).distinct()) {
                if (found.size >= MAX_SHARES) break
                val share = runCatching { session.connectShare(name) }.getOrNull() ?: continue
                val readable = runCatching {
                    (share as? DiskShare)?.list("") != null
                }.getOrDefault(false)
                runCatching { share.close() }
                if (readable) found.add(name)
            }
        } catch (error: Exception) {
            throw mapError(error, host)
        } finally {
            runCatching { client.close() }
        }
        found.toList()
    }

    fun <T> useShare(sourceId: String, block: (DiskShare, SourceConfig) -> T): T = synchronized(lock) {
        val entry = ensure(sourceId)
        try {
            block(entry.share, configurations.getValue(sourceId).config)
        } catch (error: Exception) {
            // 断线只重连一次，仍失败则交回映射后的错误。
            close(sourceId)
            val retry = ensure(sourceId)
            try {
                block(retry.share, configurations.getValue(sourceId).config)
            } catch (second: Exception) {
                throw mapError(second, configurations[sourceId]?.config?.host.orEmpty())
            }
        }
    }

    fun openFile(sourceId: String, path: String): SmbOpenFile = synchronized(lock) {
        val entry = ensure(sourceId)
        // 浏览时 RemoteEntry.path 是相对 basePath 的，播放必须把 basePath 补回去，
        // 否则把共享里的某个子目录设为起点时，所有歌曲都会找不到。
        val base = configurations[sourceId]?.config?.basePath
            ?.trim('/')?.replace('/', '\\').orEmpty()
        val smbPath = listOf(base, path.replace('/', '\\'))
            .filter { it.isNotBlank() }
            .joinToString("\\")
        val handle = entry.share.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        val size = runCatching {
            entry.share.getFileInformation(smbPath).standardInformation.endOfFile
        }.getOrDefault(UNKNOWN_LENGTH)
        SmbOpenFile(handle, size)
    }

    fun close(sourceId: String) = synchronized(lock) {
        entries.remove(sourceId)?.let { entry ->
            runCatching { entry.share.close() }
            runCatching { entry.session.close() }
            runCatching { entry.connection.close() }
            runCatching { entry.client.close() }
        }
        Unit
    }

    private fun ensure(sourceId: String): Entry {
        entries[sourceId]?.let { existing ->
            if (existing.connection.isConnected) return existing
            close(sourceId)
        }
        val saved = configurations[sourceId]
            ?: throw SourceError.Unknown(IllegalStateException("Missing SMB configuration"))
        val config = saved.config
        val host = config.host?.takeIf { it.isNotBlank() }
            ?: throw SourceError.Protocol("MissingHost")
        if (!LanAddress.isPrivateOrResolvableToPrivate(host)) {
            // 短主机名解析失败和「公网地址被拦」是两回事，给用户不同的下一步指引。
            val shortName = !host.contains('.') && !host.contains(':')
            throw if (shortName) SourceError.Protocol("HOST_UNRESOLVED") else NonLanAddressException(host)
        }
        val client = SMBClient(smbConfig())
        try {
            val connection = client.connect(host, config.port ?: 445)
            val session = connection.authenticate(authentication(saved, config))
            val share = session.connectShare(config.share.orEmpty()) as DiskShare
            val entry = Entry(client, connection, session, share)
            entries[sourceId] = entry
            return entry
        } catch (error: Exception) {
            runCatching { client.close() }
            throw mapError(error, host)
        }
    }

    private fun smbConfig(): SmbConfig = SmbConfig.builder()
        .withTimeout(10, TimeUnit.SECONDS)
        .withSoTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun authentication(saved: Credentials, config: SourceConfig): AuthenticationContext =
        if (config.anonymous) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                saved.username.orEmpty(),
                saved.password?.toCharArray() ?: CharArray(0),
                config.domain.orEmpty(),
            )
        }

    private fun mapError(error: Throwable, host: String): Throwable = when {
        error is SourceError -> error
        error is NonLanAddressException -> SourceError.Protocol("NON_LAN")
        error is SMBApiException -> {
            val status = runCatching { error.status?.name.orEmpty() }.getOrDefault("")
            when {
                status.contains("LOGON_FAILURE") || status.contains("ACCESS_DENIED") ||
                    status.contains("WRONG_PASSWORD") -> SourceError.AuthFailed()
                status.contains("BAD_NETWORK_NAME") -> SourceError.ShareNotFound(host)
                status.contains("OBJECT_NAME_NOT_FOUND") || status.contains("OBJECT_PATH_NOT_FOUND") ||
                    status.contains("FILE_NOT_FOUND") -> SourceError.PathNotFound(host)
                else -> SourceError.Protocol(
                    "SMB_STATUS:" + status.ifBlank { error.javaClass.simpleName },
                )
            }
        }
        error is SocketTimeoutException -> SourceError.Timeout()
        error is java.net.ConnectException || error is java.net.UnknownHostException ||
            error is java.io.IOException -> SourceError.NetworkUnreachable(host)
        else -> SourceError.Unknown(error)
    }
}
