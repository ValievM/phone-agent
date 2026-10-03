package com.example.phoneagent.ui.voice

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.voice.RecognizerOutcome
import com.example.phoneagent.voice.SpeechRequestBridge

@Composable
fun VoiceHost(bridge: SpeechRequestBridge) {
    val pending by bridge.pending.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            bridge.complete(RecognizerOutcome.Returned(result.resultCode, result.data))
        }

    LaunchedEffect(pending?.id) {
        val request = pending ?: return@LaunchedEffect
        if (!request.launched) {
            bridge.markLaunched(request.id)
            try {
                launcher.launch(request.intent)
            } catch (_: ActivityNotFoundException) {
                bridge.complete(RecognizerOutcome.LaunchFailed)
            }
        }
    }
}
