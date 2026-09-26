package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
private fun previewDescription(): CallDescription =
    CallDescription(
        title = stringResource(R.string.preview_tool_call_title),
        summary = stringResource(R.string.preview_tool_call_summary),
        details = listOf(stringResource(R.string.preview_tool_call_details)),
    )

@Composable
internal fun previewTimeDescription(): CallDescription =
    CallDescription(
        title = stringResource(R.string.preview_tool_call_time_title),
        summary = stringResource(R.string.preview_tool_call_time_summary),
        details = listOf(stringResource(R.string.preview_tool_call_time_details)),
    )

@Composable
private fun previewFileSearchDescription(): CallDescription =
    CallDescription(
        title = stringResource(R.string.preview_file_search_title),
        summary = stringResource(R.string.preview_file_search_summary),
        details = listOf(stringResource(R.string.preview_file_search_details)),
    )

internal fun previewTimeArguments() = buildJsonObject {}

private fun previewFileSearchArguments() =
    buildJsonObject {
        put("query", "договор")
    }

private const val PREVIEW_ALARM_HOUR = 7

private const val PREVIEW_ALARM_MINUTE = 30

private const val PREVIEW_FILE_SIZE_BYTES = 1_200L

private fun previewArguments() =
    buildJsonObject {
        put("hour", PREVIEW_ALARM_HOUR)
        put("minute", PREVIEW_ALARM_MINUTE)
        put("label", "Работа")
    }

@Composable
internal fun ToolCallCardPreviewFrame(
    state: ToolCallStatus,
    expanded: Boolean = false,
    fileHits: List<FileHit> = emptyList(),
    description: CallDescription? = null,
    arguments: JsonObject = previewArguments(),
) {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(AppTheme.spacing.large)) {
                ToolCallCard(
                    state = state,
                    description = description ?: previewDescription(),
                    arguments = arguments,
                    expanded = expanded,
                    onExpandToggle = {},
                    fileHits = fileHits,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ToolCallCardAwaitingApprovalPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.AwaitingApproval)
}

@PreviewLightDark
@Composable
private fun ToolCallCardRunningPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Running)
}

@PreviewLightDark
@Composable
private fun ToolCallCardFilesFoundPreview() {
    ToolCallCardPreviewFrame(
        state = ToolCallStatus.FilesFound(1),
        expanded = true,
        fileHits =
            listOf(
                FileHit(
                    "Договор.pdf",
                    "content://media/external/file/1",
                    PREVIEW_FILE_SIZE_BYTES,
                    "application/pdf",
                ),
            ),
        description = previewFileSearchDescription(),
        arguments = previewFileSearchArguments(),
    )
}
