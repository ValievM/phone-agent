package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.icons.AppIcons
import com.example.phoneagent.ui.designsystem.theme.AppMonospace
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

private fun JsonObject.toPrettyString(): String = prettyJson.encodeToString(JsonObject.serializer(), this)

/**
 * Confirmation sheet for consequential tool calls. Everything shown comes from the
 * [ApprovalRequest] description built by the tool itself (structured data), so the model
 * cannot fake a confirmation inside its reply. Focus lands on the title, not on
 * "Разрешить", and closing the sheet by back gesture or swipe counts as rejection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    arguments: JsonObject,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    initialDetailsExpanded: Boolean = false,
) {
    ModalBottomSheet(
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

@Composable
private fun SheetContent(
    request: ApprovalRequest,
    arguments: JsonObject,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    initialDetailsExpanded: Boolean,
) {
    val titleFocus = remember { FocusRequester() }
    var detailsExpanded by rememberSaveable { mutableStateOf(initialDetailsExpanded) }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.large)
                .padding(bottom = AppTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.approval_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics { heading() },
        )
        DescriptionBlock(description = request.description)
        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
            Text(stringResource(R.string.approval_sheet_details))
            Icon(
                painter = painterResource(AppIcons.expandMore),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detailsExpanded) {
            ExpandedDetails(
                arguments = arguments,
                details = request.description.details,
            )
        }
        ApprovalButtons(
            onApprove = onApprove,
            onReject = onReject,
        )
    }
}

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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppMonospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
        details.forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApprovalButtons(
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.approval_reject))
        }
        Button(
            onClick = onApprove,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.approval_approve))
        }
    }
}
