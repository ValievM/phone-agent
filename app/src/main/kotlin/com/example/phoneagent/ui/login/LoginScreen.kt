package com.example.phoneagent.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
fun LoginRoute(
    authenticatedContent: @Composable (onSignOut: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    // Not rememberSaveable: the raw key must not be serialized into the saved-instance Bundle
    // (persisted to disk across process death). It only needs to survive recompositions.
    var apiKey by remember { mutableStateOf("") }

    if (state.isSignedIn) {
        authenticatedContent(viewModel::signOut)
    } else {
        LoginScreen(
            state = state,
            form = LoginFormState(showApiKey, apiKey),
            onEvent = { event ->
                when (event) {
                    is LoginUiEvent.ApiKeyChanged -> {
                        apiKey = event.value
                    }

                    LoginUiEvent.ShowApiKey -> {
                        showApiKey = true
                    }

                    LoginUiEvent.BrowserSignIn -> {
                        viewModel.signInWithBrowser()
                    }

                    LoginUiEvent.ApiKeySignIn -> {
                        viewModel.signInWithApiKey(apiKey)
                        apiKey = ""
                    }

                    LoginUiEvent.DeviceCodeSignIn -> {
                        viewModel.signInWithDeviceCode()
                    }

                    LoginUiEvent.OpenDevicePage -> {
                        viewModel.openDeviceVerificationPage()
                    }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    form: LoginFormState,
    onEvent: (LoginUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(AppTheme.spacing.medium)
                    .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            LoginContent(state, form, onEvent)
        }
    }
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    form: LoginFormState,
    onEvent: (LoginUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Text(text = stringResource(R.string.login_logo), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.login_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (state.sessionExpired) {
            StatusCard(stringResource(R.string.login_session_expired))
        }
        state.error?.let { error -> ErrorCard(stringResource(error.messageResource())) }
        state.deviceCode?.let { code ->
            DeviceCodeCard(
                code = code,
                verificationUrl = state.deviceVerificationUrl.orEmpty(),
                onOpen = { onEvent(LoginUiEvent.OpenDevicePage) },
            )
        }

        LoginActions(state, form, onEvent)

        if (state.showDeviceCodeFallback) {
            TextButton(onClick = { onEvent(LoginUiEvent.DeviceCodeSignIn) }, enabled = !state.isLoading) {
                Text(stringResource(R.string.login_use_device_code))
            }
        }
        if (state.isLoading && state.deviceCode == null) CircularProgressIndicator()
        Spacer(Modifier.weight(1f, fill = false))
        Text(
            text = stringResource(R.string.login_storage_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoginActions(
    state: LoginUiState,
    form: LoginFormState,
    onEvent: (LoginUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        if (form.showApiKey) {
            ApiKeyForm(
                apiKey = form.apiKey,
                enabled = !state.isLoading,
                onApiKeyChange = { onEvent(LoginUiEvent.ApiKeyChanged(it)) },
                onSubmit = { onEvent(LoginUiEvent.ApiKeySignIn) },
            )
        } else {
            Button(
                onClick = { onEvent(LoginUiEvent.BrowserSignIn) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.sessionExpired) R.string.login_again else R.string.login_openai))
            }
            TextButton(onClick = { onEvent(LoginUiEvent.ShowApiKey) }, enabled = !state.isLoading) {
                Text(stringResource(R.string.login_use_api_key))
            }
        }
    }
}

@Composable
private fun ApiKeyForm(
    apiKey: String,
    enabled: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.login_api_key_label)) },
            placeholder = { Text(stringResource(R.string.login_api_key_placeholder)) },
            supportingText = { Text(stringResource(R.string.login_api_key_helper)) },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(onClick = onSubmit, enabled = enabled && apiKey.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_api_key_submit))
        }
    }
}

@Composable
private fun DeviceCodeCard(
    code: String,
    verificationUrl: String,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
            Text(stringResource(R.string.login_device_code_title), style = MaterialTheme.typography.titleMedium)
            Text(code, style = MaterialTheme.typography.headlineMedium)
            Text(verificationUrl, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.login_device_code_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_open_device_page)) }
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(AppTheme.spacing.medium))
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(message, modifier = Modifier.padding(AppTheme.spacing.medium), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

private fun LoginError.messageResource(): Int =
    when (this) {
        LoginError.INVALID_API_KEY -> R.string.login_error_invalid_api_key
        LoginError.LOOPBACK_PORT_UNAVAILABLE -> R.string.login_error_port_busy
        LoginError.TIMEOUT -> R.string.login_error_timeout
        LoginError.NETWORK -> R.string.login_error_network
        LoginError.GENERIC -> R.string.login_error_generic
    }
