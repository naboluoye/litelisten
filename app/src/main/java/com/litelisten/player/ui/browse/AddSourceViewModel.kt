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

sealed interface AddStatus {
    data object Idle : AddStatus
    data object Testing : AddStatus
    data class Ok(val detail: String) : AddStatus
    data class Failed(@StringRes val messageRes: Int) : AddStatus
    data class Saved(val id: String) : AddStatus
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val repository: SourceRepositoryImpl,
) : ViewModel() {
    private val _status = MutableStateFlow<AddStatus>(AddStatus.Idle)
    val status: StateFlow<AddStatus> = _status.asStateFlow()

    fun newConfig(type: SourceType, form: ServerForm): SourceConfig = SourceConfig(
        id = type.name.lowercase() + "-" + UUID.randomUUID().toString(),
        type = type,
        name = form.name.ifBlank { form.host.ifBlank { form.url } },
        host = form.host.takeIf { it.isNotBlank() },
        port = form.port.toIntOrNull(),
        share = form.share.takeIf { it.isNotBlank() },
        basePath = form.basePath.ifBlank { "/" },
        username = form.username.takeIf { it.isNotBlank() && !form.anonymous },
        domain = form.domain.takeIf { it.isNotBlank() },
        anonymous = form.anonymous,
        useTls = form.url.startsWith("https", true),
        url = form.url.takeIf { it.isNotBlank() },
    )

    private fun credentials(form: ServerForm): Credentials? =
        if (form.anonymous) null else Credentials(form.username, form.password)

    fun test(config: SourceConfig, form: ServerForm) {
        viewModelScope.launch {
            _status.value = AddStatus.Testing
            repository.test(config, credentials(form)).fold(
                onSuccess = { _status.value = AddStatus.Ok(it.displayName) },
                onFailure = { _status.value = AddStatus.Failed(messageRes(it)) },
            )
        }
    }

    fun save(config: SourceConfig, form: ServerForm) {
        viewModelScope.launch {
            _status.value = AddStatus.Testing
            val tested = repository.test(config, credentials(form))
            if (tested.isFailure) {
                _status.value = AddStatus.Failed(messageRes(tested.exceptionOrNull()))
                return@launch
            }
            repository.add(config, credentials(form)).fold(
                onSuccess = { _status.value = AddStatus.Saved(it.id) },
                onFailure = { _status.value = AddStatus.Failed(messageRes(it)) },
            )
        }
    }

    fun reset() { _status.value = AddStatus.Idle }

    private fun messageRes(error: Throwable?): Int =
        if (error is SourceError) UiMessage.of(error) else R.string.error_generic
}
