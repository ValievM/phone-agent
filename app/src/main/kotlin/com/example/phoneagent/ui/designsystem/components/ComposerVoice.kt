package com.example.phoneagent.ui.designsystem.components

/** Voice actions exposed by the composer without depending on chat screen state. */
data class ComposerVoice(
    val listening: Boolean,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
)
