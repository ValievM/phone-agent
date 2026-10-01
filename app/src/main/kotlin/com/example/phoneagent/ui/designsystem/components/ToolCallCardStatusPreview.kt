package com.example.phoneagent.ui.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R

@PreviewLightDark
@Composable
private fun ToolCallCardWaitingPermissionPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.WaitingPermission)
}

@Preview(name = "Permission · 320dp · 200%", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun ToolCallCardWaitingPermissionNarrowPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.WaitingPermission)
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ToolCallCardHandedOffPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.HandedOff("Часы"))
}

@Preview(name = "Handoff · 320dp · 200%", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun ToolCallCardHandedOffLongTargetPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.HandedOff("Будильники и таймеры на телефоне, приложение Часы"))
}

@PreviewLightDark
@Composable
private fun ToolCallCardFailedPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Failed(reason = stringResource(R.string.preview_tool_call_reason)))
}

@PreviewLightDark
@Composable
private fun ToolCallCardNotShownPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.NotShown)
}

@PreviewLightDark
@Composable
private fun ToolCallCardRejectedPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Rejected)
}

@PreviewLightDark
@Composable
private fun ToolCallCardCancelledPreview() {
    ToolCallCardPreviewFrame(ToolCallStatus.Cancelled)
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ToolCallCardExpandedPreview() {
    ToolCallCardPreviewFrame(
        state = ToolCallStatus.Done(summary = stringResource(R.string.preview_tool_call_result)),
        expanded = true,
        description = previewTimeDescription(),
        arguments = previewTimeArguments(),
    )
}

@PreviewLightDark
@Composable
private fun ToolCallCardDonePreview() {
    ToolCallCardPreviewFrame(
        state = ToolCallStatus.Done(summary = stringResource(R.string.preview_tool_call_result)),
        description = previewTimeDescription(),
        arguments = previewTimeArguments(),
    )
}
