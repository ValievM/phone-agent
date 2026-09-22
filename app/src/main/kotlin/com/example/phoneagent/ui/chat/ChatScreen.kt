package com.example.phoneagent.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.domain.agent.AgentError
import com.example.phoneagent.ui.designsystem.components.FileHit
import com.example.phoneagent.ui.designsystem.components.ToolCallCard
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@Composable
fun ChatRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ChatScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSettings = onOpenSettings,
        onOpenFile = { fileHit ->
            if (!fileHit.hasContentUriScheme()) {
                Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
            } else {
                val viewIntent =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(fileHit.uri), fileHit.mime ?: "*/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                openInDefaultViewer(context, viewIntent)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: (FileHit) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(AppIcons.settings),
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        ChatBody(
            state = state,
            onEvent = onEvent,
            onOpenFile = onOpenFile,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatBody(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
        ) {
            state.error?.let { error ->
                ErrorBanner(error = error, onDismiss = { onEvent(ChatUiEvent.DismissError) })
            }
            if (state.isRunning) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.model_working),
                        modifier = Modifier.padding(AppTheme.spacing.small),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            MessageList(
                messages = state.messages,
                onEvent = onEvent,
                onOpenFile = onOpenFile,
                modifier =
                    Modifier
                        .weight(1f)
                        .imeNestedScroll(),
            )
            HorizontalDivider()
            ChatComposer(
                enabled = !state.isRunning,
                onSubmit = { onEvent(ChatUiEvent.Submit(it)) },
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
            ) {
                Text(
                    text = stringResource(R.string.chat_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.chat_empty_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            reverseLayout = true,
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            items(
                items = messages.asReversed(),
                key = ChatMessage::id,
                contentType = ChatMessage::role,
            ) { message ->
                Message(message, onEvent, onOpenFile)
            }
        }
    }
}

@Composable
private fun Message(
    message: ChatMessage,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenFile: (FileHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolCall = message.toolCall
    if (toolCall != null) {
        ToolCallCard(
            state = toolCall.status,
            description = toolCall.description,
            arguments = toolCall.arguments,
            expanded = toolCall.expanded,
            onExpandToggle = { onEvent(ChatUiEvent.ToggleToolCallDetails(message.id)) },
            modifier = modifier,
            fileHits = toolCall.fileHits,
            onOpenFile = onOpenFile,
        )
    } else {
        BubbleMessage(message, modifier)
    }
}

@Composable
private fun BubbleMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val messageModifier =
            Modifier
                .widthIn(max = 560.dp)
                .then(
                    if (isUser) {
                        Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(
                                horizontal = AppTheme.spacing.medium,
                                vertical = AppTheme.spacing.small,
                            )
                    } else {
                        Modifier
                    },
                )
        if (isUser) {
            Text(
                text = message.text,
                modifier = messageModifier,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            SelectionContainer(modifier = messageModifier) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        if (draft.isNotBlank() && enabled) {
            onSubmit(draft)
            draft = ""
            keyboardController?.hide()
        }
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding()
                .padding(AppTheme.spacing.medium),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            label = { Text(stringResource(R.string.message_hint)) },
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        FilledIconButton(
            onClick = submit,
            enabled = enabled && draft.isNotBlank(),
        ) {
            Icon(
                painter = painterResource(AppIcons.send),
                contentDescription = stringResource(R.string.send_message),
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    error: AgentError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message =
        when (error) {
            AgentError.Offline -> stringResource(R.string.error_offline)
            else -> stringResource(R.string.error_generic)
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(modifier = Modifier.padding(AppTheme.spacing.extraSmall))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.dismiss_error))
        }
    }
}

/**
 * Opens the file in the default viewer app; only when there is none, falls back to the chooser,
 * and surfaces a readable failure when nothing can handle the intent.
 */
private fun openInDefaultViewer(
    context: Context,
    viewIntent: Intent,
) {
    try {
        context.startActivity(viewIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent.createChooser(
                    viewIntent,
                    context.getString(R.string.file_open_chooser_title),
                ),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
        }
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
    }
}
