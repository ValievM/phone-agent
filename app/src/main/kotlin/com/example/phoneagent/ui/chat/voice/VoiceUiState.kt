package com.example.phoneagent.ui.chat.voice

import com.example.phoneagent.domain.speech.SpeechFailure

sealed interface VoiceUiState {
    data object Idle : VoiceUiState

    data class Listening(
        val partial: String,
    ) : VoiceUiState

    data class Result(
        val text: String,
        val id: Long,
    ) : VoiceUiState

    data class Error(
        val reason: SpeechFailure,
    ) : VoiceUiState
}

sealed interface VoiceUiEvent {
    data object Start : VoiceUiEvent

    data object Stop : VoiceUiEvent

    data class ResultConsumed(
        val id: Long,
    ) : VoiceUiEvent

    data object DismissError : VoiceUiEvent
}
