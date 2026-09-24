package com.example.phoneagent.ui.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.ui.designsystem.theme.AppTheme

private val ListMaxHeight = 320.dp

/**
 * One confirmation for several calls of the same model response (e.g. five alarms). Every row comes
 * from the tool's own description ([ApprovalRequest.description]), never from the model's text, so
 * the user sees exactly what will run. It is all or nothing; back gesture or swipe counts as
 * "reject all", like [ApprovalSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchApprovalSheet(
    requests: List<ApprovalRequest>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onReject,
        modifier = modifier,
    ) {
        BatchSheetContent(requests = requests, onApprove = onApprove, onReject = onReject)
    }
}

@Composable
private fun BatchSheetContent(
    requests: List<ApprovalRequest>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val titleFocus = remember { FocusRequester() }
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
            text = pluralStringResource(R.plurals.batch_approval_title, requests.size, requests.size),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .focusable()
                    .semantics { heading() },
        )
        Column(
            modifier =
                Modifier
                    .heightIn(max = ListMaxHeight)
                    .verticalScroll(rememberScrollState()),
        ) {
            requests.forEachIndexed { index, request ->
                if (index > 0) HorizontalDivider()
                BatchRow(request)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)) {
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.batch_approval_reject_all))
            }
            Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.batch_approval_approve_all))
            }
        }
    }
}

@Composable
private fun BatchRow(request: ApprovalRequest) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.extraSmall),
    ) {
        Text(
            text = request.description.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = request.description.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
