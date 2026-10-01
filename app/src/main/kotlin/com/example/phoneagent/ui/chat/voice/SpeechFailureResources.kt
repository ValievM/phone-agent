package com.example.phoneagent.ui.chat.voice

import com.example.phoneagent.R
import com.example.phoneagent.domain.speech.SpeechFailure

fun SpeechFailure.messageRes(): Int? =
    when (this) {
        SpeechFailure.CANCELLED -> null
        SpeechFailure.NO_MATCH -> R.string.voice_error_no_match
        SpeechFailure.NO_SPEECH -> R.string.voice_error_no_speech
        SpeechFailure.NETWORK -> R.string.voice_error_network
        SpeechFailure.AUDIO -> R.string.voice_error_audio
        SpeechFailure.BUSY -> R.string.voice_error_busy
        SpeechFailure.PERMISSION_DENIED -> R.string.voice_error_permission_denied
        SpeechFailure.LANGUAGE_UNAVAILABLE -> R.string.voice_error_language_unavailable
        SpeechFailure.UNAVAILABLE -> R.string.voice_error_unavailable
        SpeechFailure.UNKNOWN -> R.string.voice_error_unknown
    }
