package com.litelisten.player.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.component.LiteListenTopBar
import com.litelisten.player.core.designsystem.theme.LocalSpacing
import com.litelisten.player.core.model.SourceConfig
import com.litelisten.player.core.model.SourceType

@Composable
fun AddSourceScreen(
    type: SourceType,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddSourceViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    var form by remember {
        mutableStateOf(
            ServerForm(port = if (type == SourceType.SMB) "445" else "80"),
        )
    }
    var showPassword by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<Int?>(null) }
    // id 在页面生命周期内固定，避免测试与保存生成两套凭据。
    val stableId = remember { viewModel.newConfig(type, ServerForm()).id }
    fun currentConfig(): SourceConfig = viewModel.newConfig(type, form).copy(id = stableId)

    fun validate(): Int? = when {
        type == SourceType.SMB && form.host.isBlank() -> R.string.validation_host_empty
        type == SourceType.SMB && (form.port.toIntOrNull() ?: 0) !in 1..65535 -> R.string.validation_port_invalid
        type == SourceType.SMB && form.share.isBlank() -> R.string.validation_share_empty
        type == SourceType.WEBDAV && !form.url.startsWith("http", true) -> R.string.validation_url_invalid
        !form.anonymous && form.username.isBlank() && type == SourceType.SMB -> R.string.validation_username_empty
        else -> null
    }

    LaunchedEffect(status) {
        if (status is AddStatus.Saved) {
            onSaved((status as AddStatus.Saved).id)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.l),
    ) {
        LiteListenTopBar(
            title = stringResource(
                if (type == SourceType.SMB) R.string.add_smb_title else R.string.add_webdav_title,
            ),
            onNavigateUp = onBack,
        )
        Field(stringResource(R.string.field_name), form.name) { form = form.copy(name = it) }
        if (type == SourceType.SMB) {
            Field(stringResource(R.string.field_host), form.host) { form = form.copy(host = it) }
            Field(stringResource(R.string.field_port), form.port) { form = form.copy(port = it) }
            Field(stringResource(R.string.field_share), form.share) { form = form.copy(share = it) }
            Field(stringResource(R.string.field_base_path), form.basePath) { form = form.copy(basePath = it) }
            Field(stringResource(R.string.field_domain), form.domain) { form = form.copy(domain = it) }
        } else {
            Field(stringResource(R.string.field_url), form.url) { form = form.copy(url = it) }
            Text(
                stringResource(R.string.webdav_https_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.anonymous, onCheckedChange = { form = form.copy(anonymous = it) })
            Text(stringResource(R.string.field_anonymous))
        }
        if (!form.anonymous) {
            Field(stringResource(R.string.field_username), form.username) { form = form.copy(username = it) }
            OutlinedTextField(
                value = form.password,
                onValueChange = { form = form.copy(password = it) },
                label = { Text(stringResource(R.string.field_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showPassword, onCheckedChange = { showPassword = it })
                Text(stringResource(R.string.field_show_password))
            }
        }

        validation?.let { res ->
            Text(
                stringResource(res),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when (val current = status) {
            is AddStatus.Ok -> Text(
                stringResource(R.string.test_success, current.detail),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            is AddStatus.Failed -> Text(
                stringResource(current.messageRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            AddStatus.Testing -> Text(
                stringResource(R.string.action_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Unit
        }

        Row(modifier = Modifier.padding(vertical = spacing.l)) {
            OutlinedButton(
                onClick = {
                    validation = validate()
                    if (validation == null) viewModel.test(currentConfig(), form)
                },
                modifier = Modifier.weight(1f).padding(end = spacing.s),
                enabled = status !is AddStatus.Testing,
            ) { Text(stringResource(R.string.action_test_connection)) }
            Button(
                onClick = {
                    validation = validate()
                    if (validation == null) viewModel.save(currentConfig(), form)
                },
                modifier = Modifier.weight(1f),
                enabled = status !is AddStatus.Testing,
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = com.litelisten.player.core.designsystem.theme.LocalSpacing.current.s),
    )
}
