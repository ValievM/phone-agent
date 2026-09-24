package com.example.phoneagent.ui.designsystem.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.phoneagent.ui.designsystem.theme.AppTheme

@PreviewLightDark
@PreviewFontScale
@Composable
private fun IdleComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(enabled = true, isRunning = false, onSubmit = {}, onStop = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun RunningComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(enabled = false, isRunning = true, onSubmit = {}, onStop = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun UnavailableComposerPreview() {
    AppTheme(useDynamicColor = false) {
        Surface {
            Composer(enabled = false, isRunning = false, onSubmit = {}, onStop = {})
        }
    }
}
