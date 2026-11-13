package com.litelisten.player.core.source.smb

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
import com.litelisten.player.core.common.LanAddress
import com.litelisten.player.core.common.NonLanAddressException
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import java.net.SocketTimeoutException
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SmbOpenFile(val handle: SmbFile, val size: Long)

/** smbj 对象非线程安全，所有共享操作串行；凭据只留内存，不写日志。 */
@Singleton
class SmbConnectionPool @Inject constructor() {
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
    private companion object { const val UNKNOWN_LENGTH = -1L }

    fun configure(config: SourceConfig, username: String?, password: String?) {
        configurations[config.id] = Credentials(config, username, password)
        close(config.id)
    }

    fun forget(sourceId: String) {
        configurations.remove(sourceId)
        close(sourceId)
    }

    fun configOf(sourceId: String): SourceConfig? = configurations[sourceId]?.config

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
        if (!LanAddress.isPrivateOrResolvableToPrivate(host)) throw NonLanAddressException(host)
        val smbConfig = SmbConfig.builder()
            .withTimeout(10, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(smbConfig)
        try {
            val connection = client.connect(host, config.port ?: 445)
            val auth = if (config.anonymous) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    saved.username.orEmpty(),
                    saved.password?.toCharArray() ?: CharArray(0),
                    config.domain.orEmpty(),
                )
            }
            val session = connection.authenticate(auth)
            val share = session.connectShare(config.share.orEmpty()) as DiskShare
            val entry = Entry(client, connection, session, share)
            entries[sourceId] = entry
            return entry
        } catch (error: Exception) {
            runCatching { client.close() }
            throw mapError(error, host)
        }
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
                else -> SourceError.Unknown(error)
            }
        }
        error is SocketTimeoutException -> SourceError.Timeout()
        error is java.net.ConnectException || error is java.net.UnknownHostException ||
            error is java.io.IOException -> SourceError.NetworkUnreachable(host)
        else -> SourceError.Unknown(error)
    }
}
