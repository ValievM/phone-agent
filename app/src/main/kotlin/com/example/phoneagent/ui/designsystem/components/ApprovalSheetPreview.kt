package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.ArgumentReview
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.EditableField
import com.example.phoneagent.domain.tools.EditableFieldKind
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolArgumentEditor
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val PREVIEW_ALARM_HOUR = 7

private const val PREVIEW_ALARM_MINUTE = 30

private const val PREVIEW_FALLBACK_ALARM_HOUR = 0

private const val PREVIEW_FALLBACK_ALARM_MINUTE = 0

private const val PREVIEW_MIN_ALARM_HOUR = 0

private const val PREVIEW_MAX_ALARM_HOUR = 23

private const val PREVIEW_MIN_ALARM_MINUTE = 0

private const val PREVIEW_MAX_ALARM_MINUTE = 59

private fun previewArguments(hour: Int = PREVIEW_ALARM_HOUR) =
    buildJsonObject {
        put("hour", hour)
        put("minute", PREVIEW_ALARM_MINUTE)
        put("message", "Работа")
    }

private val previewEditor =
    object : ToolArgumentEditor {
        override val fields =
            listOf(
                EditableField("hour", "Час", EditableFieldKind.INTEGER),
                EditableField("minute", "Минуты", EditableFieldKind.INTEGER),
                EditableField("message", "Название", EditableFieldKind.TEXT),
            )

        override fun review(args: kotlinx.serialization.json.JsonObject): ArgumentReview {
            val hour = args["hour"]?.jsonPrimitive?.content?.toIntOrNull()
            val minute = args["minute"]?.jsonPrimitive?.content?.toIntOrNull()
            val message = args["message"]?.jsonPrimitive?.content.orEmpty()
            val validationError =
                when {
                    hour == null || minute == null -> "Укажите время будильника"
                    hour !in PREVIEW_MIN_ALARM_HOUR..PREVIEW_MAX_ALARM_HOUR -> "Час должен быть от 0 до 23"
                    minute !in PREVIEW_MIN_ALARM_MINUTE..PREVIEW_MAX_ALARM_MINUTE -> "Минуты должны быть от 0 до 59"
                    else -> null
                }
            return ArgumentReview(
                CallDescription(
                    "Поставить будильник",
                    "На %02d:%02d — %s".format(hour ?: PREVIEW_FALLBACK_ALARM_HOUR, minute ?: PREVIEW_FALLBACK_ALARM_MINUTE, message),
                ),
                validationError,
            )
        }
    }

@Composable
private fun previewApprovalRequest(): ApprovalRequest =
    ApprovalRequest(
        description =
            CallDescription(
                title = stringResource(R.string.preview_tool_call_title),
                summary = stringResource(R.string.preview_tool_call_summary),
                details = listOf(stringResource(R.string.preview_tool_call_details)),
            ),
        risk = Risk.CONFIRM,
        arguments = previewArguments(),
    )

private fun previewEditableApprovalRequest(hour: Int = PREVIEW_ALARM_HOUR): ApprovalRequest {
    val arguments = previewArguments(hour)
    val review = previewEditor.review(arguments)
    return ApprovalRequest(review.description, Risk.CONFIRM, arguments, previewEditor)
}

@PreviewLightDark
@Composable
private fun ApprovalSheetPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ApprovalSheet(
                request = previewApprovalRequest(),
                arguments = previewArguments(),
                onApprove = {},
                onReject = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ApprovalSheetDetailsExpandedPreview() {
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ApprovalSheet(
                request = previewApprovalRequest(),
                arguments = previewArguments(),
                onApprove = {},
                onReject = {},
                initialDetailsExpanded = true,
            )
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EditableApprovalSheetPreview() {
    val request = previewEditableApprovalRequest()
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ApprovalSheet(
                request = request,
                arguments = request.arguments,
                onApprove = {},
                onReject = {},
            )
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun InvalidEditableApprovalSheetPreview() {
    val request = previewEditableApprovalRequest(hour = 25)
    AppTheme(useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ApprovalSheet(
                request = request,
                arguments = request.arguments,
                onApprove = {},
                onReject = {},
            )
        }
    }
}
