package com.litelisten.player.ui.browse

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litelisten.player.R
import com.litelisten.player.core.common.UiMessage
import com.litelisten.player.core.model.Credentials
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceError
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.data.repository.SourceRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ServerForm(
    val name: String = "",
    val host: String = "",
    val port: String = "445",
    val share: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val anonymous: Boolean = false,
    val basePath: String = "/",
    val url: String = "",
)

/**
 * 表单的纯计算部分（不碰 Android API，可直接跑 JVM 单元测试）。
 * 账号密码必须放在共享名 / 测试连接之前校验：绝大多数 NAS 都要求登录，
 * 先让用户填凭据，再去查共享或测试，才不会一头撞在匿名连接失败上。
 */
object ServerFormFactory {
    fun defaultPort(type: SourceType): Int = when (type) {
        SourceType.SMB -> 445
        SourceType.JELLYFIN -> 8096
        else -> 80
    }

    /** 从 http(s)://host:port/ 中拆出主机与端口；不依赖 android.net.Uri。 */
    fun parseUrl(url: String): Pair<String, Int>? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        val authority = url.substringAfter("://", "").substringBefore('/')
        if (authority.isBlank()) return null
        val host = authority.substringBeforeLast(':', authority)
            .removePrefix("[").removeSuffix("]")
        if (host.isBlank()) return null
        val port = authority.substringAfterLast(':', "").toIntOrNull()
            ?: if (url.startsWith("https", ignoreCase = true)) 443 else 80
        return host to port
    }

    fun initial(type: SourceType, host: String, url: String, port: Int?): ServerForm {
        if (type == SourceType.JELLYFIN) {
            val parsed = parseUrl(url)
            if (parsed != null) {
                return ServerForm(
                    host = parsed.first,
                    port = parsed.second.toString(),
                    url = url,
                )
            }
        }
        return ServerForm(
            host = host,
            port = (port ?: defaultPort(type)).toString(),
            url = url,
        )
    }

    /** Jellyfin 没有共享名概念，地址由 IP + 端口拼成；其余协议沿用用户填的完整 URL。 */
    fun buildUrl(type: SourceType, form: ServerForm): String {
        if (type != SourceType.JELLYFIN) return form.url.trim()
        val host = form.host.trim()
        if (host.isBlank()) return form.url.trim()
        val port = form.port.toIntOrNull() ?: defaultPort(type)
        val scheme = if (port == 8920 || port == 443) "https" else "http"
        return "$scheme://$host:$port/"
    }

    fun credentials(type: SourceType, form: ServerForm): Credentials? =
        if (type == SourceType.JELLYFIN || !form.anonymous) {
            Credentials(form.username, form.password)
        } else null

    fun validate(type: SourceType, form: ServerForm): Int? = when {
        type == SourceType.SMB && form.host.isBlank() -> R.string.validation_host_empty
        type == SourceType.SMB && (form.port.toIntOrNull() ?: 0) !in 1..65535 ->
            R.string.validation_port_invalid
        type == SourceType.JELLYFIN && form.host.isBlank() -> R.string.validation_host_empty
        type == SourceType.JELLYFIN && (form.port.toIntOrNull() ?: 0) !in 1..65535 ->
            R.string.validation_port_invalid
        type == SourceType.WEBDAV && !form.url.startsWith("http", ignoreCase = true) ->
            R.string.validation_url_invalid
        // Jellyfin 靠账号换取访问令牌，不允许匿名。
        type == SourceType.JELLYFIN && form.username.isBlank() ->
            R.string.validation_jellyfin_login
        type == SourceType.JELLYFIN && form.password.isBlank() ->
            R.string.validation_password_empty
        !form.anonymous && form.username.isBlank() -> R.string.validation_username_empty
        !form.anonymous && form.password.isBlank() -> R.string.validation_password_empty
        // 共享名放最后：用户先被引导填账号，再查共享，才不会卡在匿名连接失败上。
        type == SourceType.SMB && form.share.isBlank() -> R.string.validation_share_empty
        else -> null
    }
}

sealed interface AddStatus {
    data object Idle : AddStatus
    data object Testing : AddStatus
    data class Ok(val detail: String) : AddStatus
    data class Failed(@StringRes val messageRes: Int, val messageArg: String = "") : AddStatus
    data class Saved(val id: String) : AddStatus
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val repository: SourceRepositoryImpl,
) : ViewModel() {
    private val _status = MutableStateFlow<AddStatus>(AddStatus.Idle)
    val status: StateFlow<AddStatus> = _status.asStateFlow()
    private val _shares = MutableStateFlow<List<String>>(emptyList())
    val shares: StateFlow<List<String>> = _shares.asStateFlow()
    private val _detectingShares = MutableStateFlow(false)
    val detectingShares: StateFlow<Boolean> = _detectingShares.asStateFlow()
    private val _shareSearchDone = MutableStateFlow(false)
    val shareSearchDone: StateFlow<Boolean> = _shareSearchDone.asStateFlow()

    fun newConfig(type: SourceType, form: ServerForm): SourceConfig {
        val url = ServerFormFactory.buildUrl(type, form)
        val parsedHost = if (type == SourceType.JELLYFIN) ServerFormFactory.parseUrl(url)?.first else null
        return SourceConfig(
            id = type.name.lowercase() + "-" + UUID.randomUUID().toString(),
            type = type,
            name = form.name.ifBlank { form.host.ifBlank { url } },
            host = (form.host.takeIf { it.isNotBlank() } ?: parsedHost),
            port = form.port.toIntOrNull(),
            share = form.share.takeIf { it.isNotBlank() },
            basePath = form.basePath.ifBlank { "/" },
            username = form.username.takeIf { it.isNotBlank() && !form.anonymous },
            domain = form.domain.takeIf { it.isNotBlank() },
            anonymous = form.anonymous,
            useTls = url.startsWith("https", ignoreCase = true),
            url = url.takeIf { it.isNotBlank() },
        )
    }

    fun test(config: SourceConfig, form: ServerForm) {
        viewModelScope.launch {
            _status.value = AddStatus.Testing
            repository.test(config, ServerFormFactory.credentials(config.type, form)).fold(
                onSuccess = { _status.value = AddStatus.Ok(it.detail) },
                onFailure = { _status.value = failed(it) },
            )
        }
    }

    fun save(config: SourceConfig, form: ServerForm) {
        viewModelScope.launch {
            _status.value = AddStatus.Testing
            val credentials = ServerFormFactory.credentials(config.type, form)
            val tested = repository.test(config, credentials)
            if (tested.isFailure) {
                _status.value = failed(tested.exceptionOrNull())
                return@launch
            }
            repository.add(config, credentials).fold(
                onSuccess = { _status.value = AddStatus.Saved(it.id) },
                onFailure = { _status.value = failed(it) },
            )
        }
    }

    /** 只填了 IP 也能查共享名；失败时把真实原因显示出来，不要退回「请手动填写」了事。 */
    fun detectShares(config: SourceConfig, form: ServerForm) {
        viewModelScope.launch {
            _detectingShares.value = true
            _shares.value = emptyList()
            _shareSearchDone.value = false
            _status.value = AddStatus.Idle
            repository.listShares(config, ServerFormFactory.credentials(config.type, form)).fold(
                onSuccess = { _shares.value = it.map { share -> share.name } },
                onFailure = { _status.value = failed(it) },
            )
            _shareSearchDone.value = true
            _detectingShares.value = false
        }
    }

    fun clearShares() { _shares.value = emptyList() }

    fun reset() { _status.value = AddStatus.Idle }

    private fun failed(error: Throwable?): AddStatus.Failed =
        if (error is SourceError) {
            AddStatus.Failed(UiMessage.of(error), UiMessage.argOf(error))
        } else {
            AddStatus.Failed(R.string.error_generic)
        }
}
