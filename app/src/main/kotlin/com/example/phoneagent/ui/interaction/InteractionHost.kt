package com.example.phoneagent.ui.interaction

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.R
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.interaction.PendingInteraction
import com.example.phoneagent.interaction.UserInteractionBridge

/**
 * Root-level composable that answers whatever [UserInteractionBridge] is currently waiting on —
 * see architecture/adapters.md. Placed once, above navigation, so any screen's tool call can be
 * confirmed regardless of which screen is on top.
 *
 * The approval dialog here is a plain [AlertDialog] placeholder: the real `ApprovalSheet` (design/
 * components.md, stage 4.4) is being built on a separate branch (stage-4/tool-call-ui) and will
 * replace the `PendingInteraction.Approval` branch below without touching the bridge itself.
 */
@Composable
fun InteractionHost(bridge: UserInteractionBridge) {
    val pending by bridge.pending.collectAsStateWithLifecycle()

    DisposableEffect(bridge) {
        onDispose { bridge.resolvePendingAsDeclined() }
    }

    when (val current = pending) {
        is PendingInteraction.Approval -> {
            AlertDialog(
                onDismissRequest = { current.deferred.complete(false) },
                title = { Text(current.request.description.title) },
                text = { Text(current.request.description.summary) },
                confirmButton = {
                    TextButton(onClick = { current.deferred.complete(true) }) {
                        Text(stringResource(R.string.approval_allow))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { current.deferred.complete(false) }) {
                        Text(stringResource(R.string.approval_deny))
                    }
                },
            )
        }

        is PendingInteraction.Permission -> {
            PermissionRequest(capability = current.capability, onResult = current.deferred::complete)
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun PermissionRequest(
    capability: Capability,
    onResult: (CapabilityResult) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            currentOnResult(if (granted) CapabilityResult.Available else CapabilityResult.Denied)
        }
    DisposableEffect(capability) {
        val permission = capability.androidPermissionOrNull()
        if (permission == null) {
            // No runtime permission needed for this capability on this OS version.
            currentOnResult(CapabilityResult.Available)
        } else {
            launcher.launch(permission)
        }
        onDispose {}
    }
}

private fun Capability.androidPermissionOrNull(): String? =
    when (this) {
        Capability.NOTIFICATIONS -> {
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
        }
    }
