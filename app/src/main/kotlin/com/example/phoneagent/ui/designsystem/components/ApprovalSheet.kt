package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.EditableField
import com.example.phoneagent.domain.tools.EditableFieldKind
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppCodeTextStyle
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val json = Json {}
private val prettyJson = Json { prettyPrint = true }

private fun JsonObject.toPrettyString(): String = prettyJson.encodeToString(JsonObject.serializer(), this)

/**
 * Confirmation sheet for consequential tool calls. The description and optional editable fields
 * come from the tool, so the model cannot fake a confirmation inside its reply. Approval returns
 * the reviewed arguments. Focus lands on the title, not on "Разрешить"; closing the sheet rejects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    arguments: JsonObject,
    onApprove: (JsonObject) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    initialDetailsExpanded: Boolean = false,
) {
    if (request.risk == Risk.SENSITIVE) {
        SensitiveApprovalSheet(request = request, onApprove = { onApprove(arguments) }, onReject = onReject, modifier = modifier)
    } else {
        ConfirmationSheet(
            onDismissRequest = onReject,
            modifier = modifier,
        ) {
            SheetContent(
                request = request,
                arguments = arguments,
                onApprove = onApprove,
                onReject = onReject,
                initialDetailsExpanded = initialDetailsExpanded,
            )
        }
    }
}

@Composable
private fun SheetContent(
    request: ApprovalRequest,
    arguments: JsonObject,
    onApprove: (JsonObject) -> Unit,
    onReject: () -> Unit,
    initialDetailsExpanded: Boolean,
) {
    var detailsExpanded by rememberSaveable(request, initialDetailsExpanded) { mutableStateOf(initialDetailsExpanded) }
    val editor = request.editor?.takeIf { request.risk == Risk.CONFIRM }
    var draftJson by rememberSaveable(request, arguments) { mutableStateOf(arguments.encode()) }
    val draftArguments = remember(draftJson) { draftJson.decodeArguments() }
    val review = editor?.review(draftArguments)
    val description = review?.description ?: request.description
    SheetLayout(
        title = stringResource(R.string.approval_sheet_title),
        footer = {
            ButtonPair(
                spacing = AppTheme.spacing.medium,
                secondary = { SecondaryButton(onClick = onReject) { Text(stringResource(R.string.approval_reject)) } },
                primary = {
                    PrimaryButton(
                        onClick = { onApprove(if (editor == null) arguments else draftArguments) },
                        enabled = review?.validationError == null,
                    ) { Text(stringResource(R.string.approval_approve)) }
                },
            )
        },
    ) {
        DescriptionBlock(description = description)
        EditableArgumentFields(editor?.fields.orEmpty(), draftArguments) { updated -> draftJson = updated.encode() }
        review?.validationError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        QuietButton(onClick = { detailsExpanded = !detailsExpanded }) {
            Text(stringResource(R.string.approval_sheet_details))
            Icon(
                painter = painterResource(AppIcons.expandMore),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detailsExpanded) {
            ExpandedDetails(
                arguments = draftArguments,
                details = description.details,
            )
        }
    }
}

@Composable
private fun EditableArgumentFields(
    fields: List<EditableField>,
    arguments: JsonObject,
    onArgumentsChange: (JsonObject) -> Unit,
) {
    if (fields.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
        fields.forEach { field ->
            EditableArgumentField(
                field = field,
                value = (arguments[field.key] as? JsonPrimitive)?.content.orEmpty(),
                onValueChange = { value -> onArgumentsChange(JsonObject(arguments + (field.key to field.primitive(value)))) },
            )
        }
    }
}

@Composable
private fun EditableArgumentField(
    field: EditableField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(field.label) },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (field.kind == EditableFieldKind.INTEGER) KeyboardType.Number else KeyboardType.Text,
            ),
        singleLine = true,
    )
}

private fun EditableField.primitive(value: String): JsonPrimitive =
    when (kind) {
        EditableFieldKind.INTEGER -> value.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(value)
        EditableFieldKind.TEXT -> JsonPrimitive(value)
    }

private fun JsonObject.encode(): String = json.encodeToString(JsonObject.serializer(), this)

private fun String.decodeArguments(): JsonObject = json.parseToJsonElement(this) as JsonObject

@Composable
private fun DescriptionBlock(description: CallDescription) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
        Text(
            text = description.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpandedDetails(
    arguments: JsonObject,
    details: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall)) {
        Text(
            text = arguments.toPrettyString(),
            style = AppCodeTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
