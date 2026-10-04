package com.example.phoneagent.ui.designsystem.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.coroutines.delay

private const val COPY_CONFIRMATION_MILLIS = 1_500L
const val ASSISTANT_MESSAGE_ACTIONS_TAG = "assistant_message_actions"

private val MessageMaxWidth = 560.dp

/** A chooser intent for sharing plain answer text; empty answers have no share action. */
fun shareIntent(text: String): Intent? {
    if (text.isEmpty()) return null
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    return Intent.createChooser(sendIntent, null)
}

/** The user's own message: right-aligned, on a tonal bubble. */
@Composable
fun UserMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        // A user bubble never runs the full width: the gap on the left tells it from a model reply.
        modifier = modifier.fillMaxWidth().padding(start = AppTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = text,
            modifier =
                Modifier
                    .widthIn(max = MessageMaxWidth)
                    .clip(AppTheme.shapeTokens.userBubble)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(
                        horizontal = AppTheme.spacing.medium,
                        vertical = AppTheme.spacing.compact,
                    ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The model's reply: no bubble, full width, rendered as markdown. A tapped link never opens on its
 * own: it goes through [LinkConfirmationSheet] first and only then reaches [onOpenLink].
 */
@Composable
fun AssistantMessage(
    text: String,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
    showActions: Boolean = true,
) {
    var pendingLink by remember { mutableStateOf<LinkDecision?>(null) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPY_CONFIRMATION_MILLIS)
            copied = false
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            AssistantMarkdown(
                text = text,
                onLinkClick = { url -> pendingLink = evaluateLink(url) },
                modifier = Modifier.widthIn(max = MessageMaxWidth),
            )
        }
        if (text.isNotEmpty()) {
            AssistantMessageActions(
                copied = copied,
                showActions = showActions,
                onCopy = {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
                onShare = { shareIntent(text)?.let(context::startActivity) },
            )
        }
    }
    pendingLink?.let { decision ->
        LinkConfirmationSheet(
            decision = decision,
            onOpen = { url ->
                pendingLink = null
                onOpenLink(url)
            },
            onDismiss = { pendingLink = null },
        )
    }
}

@Composable
internal fun AssistantMessageActions(
    copied: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    showActions: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(ASSISTANT_MESSAGE_ACTIONS_TAG),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (showActions) {
            IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(if (copied) AppIcons.check else AppIcons.copy),
                    contentDescription = stringResource(if (copied) R.string.answer_copied else R.string.copy_answer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(AppIcons.share),
                    contentDescription = stringResource(R.string.share_answer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
