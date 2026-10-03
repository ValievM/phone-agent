package com.example.phoneagent.ui.chat.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceInputViewModel
    @Inject
    constructor(
        private val speechInput: SpeechInput,
    ) : ViewModel() {
        private val _state = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
        val state: StateFlow<VoiceUiState> = _state.asStateFlow()

        private var sessionJob: Job? = null
        private var nextResultId = 0L

        fun onEvent(event: VoiceUiEvent) {
            when (event) {
                VoiceUiEvent.Start -> start()
                VoiceUiEvent.Stop -> stop()
                is VoiceUiEvent.ResultConsumed -> consumeResult(event.id)
                VoiceUiEvent.DismissError -> dismissError()
            }
        }

        private fun start() {
            if (_state.value !is VoiceUiState.Idle && _state.value !is VoiceUiState.Error) return

            var level = 0f
            _state.value = VoiceUiState.Listening("", level)
            sessionJob =
                viewModelScope.launch {
                    speechInput.listen(SpeechRequest(SPEECH_LANGUAGE_TAG)).collect { event ->
                        when (event) {
                            SpeechEvent.Listening -> {
                                Unit
                            }

                            is SpeechEvent.Partial -> {
                                _state.value = VoiceUiState.Listening(event.text, level)
                            }

                            is SpeechEvent.Level -> {
                                level = if (event.normalized.isNaN()) 0f else event.normalized.coerceIn(0f, 1f)
                                val partial = (_state.value as? VoiceUiState.Listening)?.partial.orEmpty()
                                _state.value = VoiceUiState.Listening(partial, level)
                            }

                            is SpeechEvent.Final -> {
                                _state.value = VoiceUiState.Result(event.text, nextResultId++)
                            }

                            is SpeechEvent.Failed -> {
                                _state.value =
                                    if (event.reason == SpeechFailure.CANCELLED) {
                                        VoiceUiState.Idle
                                    } else {
                                        VoiceUiState.Error(event.reason)
                                    }
                            }
                        }
                    }
                }
        }

        private fun stop() {
            if (_state.value !is VoiceUiState.Listening) return
            sessionJob?.cancel()
            sessionJob = null
            _state.value = VoiceUiState.Idle
        }

        private fun consumeResult(id: Long) {
            val result = _state.value as? VoiceUiState.Result ?: return
            if (result.id == id) _state.value = VoiceUiState.Idle
        }

        private fun dismissError() {
            if (_state.value is VoiceUiState.Error) _state.value = VoiceUiState.Idle
        }

        private companion object {
            const val SPEECH_LANGUAGE_TAG = "ru-RU"
        }
    }
