package com.example.phoneagent.ui.designsystem.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppMonospace
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

private fun JsonObject.toPrettyString(): String = prettyJson.encodeToString(JsonObject.serializer(), this)

/**
 * A tool call shown in the chat feed. Collapsed by default: tool-defined [CallDescription]
 * title plus a one-line summary, status icon in the top-right corner and a status label.
 * Expanding reveals the call arguments (monospace) and the description details. Everything
 * rendered comes from structured data produced by the tool itself — never from model text.
 */
@Composable
fun ToolCallCard(
    state: ToolCallStatus,
    description: CallDescription,
    arguments: JsonObject,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable(
                    onClickLabel = stringResource(R.string.tool_call_toggle_arguments),
                    onClick = onExpandToggle,
                ),
        border =
            if (state is ToolCallStatus.AwaitingApproval) {
                BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                CardDefaults.outlinedCardBorder()
            },
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
            ) {
                Text(
                    text = description.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusVisual(state)
                Icon(
                    painter = painterResource(AppIcons.expandMore),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = description.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLabel(state)
            if (expanded) {
                ExpandedDetails(description = description, arguments = arguments)
            }
        }
    }
}

/** Monospace arguments and the tool-provided detail lines, shown when the card is expanded. */
@Composable
private fun ExpandedDetails(
    description: CallDescription,
    arguments: JsonObject,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        description.details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.tool_call_arguments),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = arguments.toPrettyString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMonospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Status mark in the card corner: an icon, or a small progress indicator while running. */
@Composable
private fun StatusVisual(state: ToolCallStatus) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        ToolCallStatus.AwaitingApproval -> {
            Icon(
                painter = painterResource(AppIcons.hourglassEmpty),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        ToolCallStatus.Running -> {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = onSurfaceVariant,
                trackColor = Color.Transparent,
            )
        }

        is ToolCallStatus.Done -> {
            Icon(
                painter = painterResource(AppIcons.checkCircle),
                contentDescription = null,
                tint = AppTheme.extendedColors.success,
                modifier = Modifier.size(20.dp),
            )
        }

        is ToolCallStatus.Failed -> {
            Icon(
                painter = painterResource(AppIcons.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }

        ToolCallStatus.Rejected -> {
            Icon(
                painter = painterResource(AppIcons.block),
                contentDescription = null,
                tint = onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        ToolCallStatus.Cancelled -> {
            Icon(
                painter = painterResource(AppIcons.stop),
                contentDescription = null,
                tint = onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Status text next to the icon mark: the status is never conveyed by color alone. */
@Composable
private fun StatusLabel(state: ToolCallStatus) {
    val (text, color) =
        when (state) {
            ToolCallStatus.AwaitingApproval -> {
                stringResource(R.string.tool_call_status_awaiting) to MaterialTheme.colorScheme.primary
            }

            ToolCallStatus.Running -> {
                stringResource(R.string.tool_call_status_running) to MaterialTheme.colorScheme.onSurfaceVariant
            }

            is ToolCallStatus.Done -> {
                state.summary to AppTheme.extendedColors.success
            }

            is ToolCallStatus.Failed -> {
                state.reason to MaterialTheme.colorScheme.error
            }

            ToolCallStatus.Rejected -> {
                stringResource(R.string.tool_call_status_rejected) to MaterialTheme.colorScheme.onSurfaceVariant
            }

            ToolCallStatus.Cancelled -> {
                stringResource(R.string.tool_call_status_cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}
