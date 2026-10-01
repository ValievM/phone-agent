package com.example.phoneagent.ui.chat.voice

import androidx.lifecycle.ViewModelStore
import com.example.phoneagent.domain.fakes.FakeSpeechInput
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechFailure
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `start then final exposes result and consuming it returns to idle`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Final("привет")) }
            val viewModel = VoiceInputViewModel(speechInput)

            viewModel.onEvent(VoiceUiEvent.Start)
            assertEquals(VoiceUiState.Listening(""), viewModel.state.value)
            advanceUntilIdle()

            val result = viewModel.state.value as VoiceUiState.Result
            assertEquals("привет", result.text)
            viewModel.onEvent(VoiceUiEvent.ResultConsumed(result.id))
            assertEquals(VoiceUiState.Idle, viewModel.state.value)
        }

    @Test
    fun `partial recognition updates listening text`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Partial("при")) }
            val viewModel = VoiceInputViewModel(speechInput)

            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()

            assertEquals(VoiceUiState.Listening("при"), viewModel.state.value)
        }

    @Test
    fun `cancelled speech failure returns to idle`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Failed(SpeechFailure.CANCELLED)) }
            val viewModel = VoiceInputViewModel(speechInput)

            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()

            assertEquals(VoiceUiState.Idle, viewModel.state.value)
        }

    @Test
    fun `recognition failure is exposed and can be dismissed`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Failed(SpeechFailure.NO_MATCH)) }
            val viewModel = VoiceInputViewModel(speechInput)

            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()
            assertEquals(VoiceUiState.Error(SpeechFailure.NO_MATCH), viewModel.state.value)

            viewModel.onEvent(VoiceUiEvent.DismissError)
            assertEquals(VoiceUiState.Idle, viewModel.state.value)
        }

    @Test
    fun `start while listening is ignored`() =
        runTest(dispatcher.scheduler) {
            val speechInput = HoldingSpeechInput()
            val viewModel = VoiceInputViewModel(speechInput)

            viewModel.onEvent(VoiceUiEvent.Start)
            viewModel.onEvent(VoiceUiEvent.Start)
            runCurrent()

            assertEquals(1, speechInput.requests.size)
            viewModel.onEvent(VoiceUiEvent.Stop)
            runCurrent()
        }

    @Test
    fun `stop cancels active speech session and releases its resources`() =
        runTest(dispatcher.scheduler) {
            val speechInput = HoldingSpeechInput()
            val viewModel = VoiceInputViewModel(speechInput)
            viewModel.onEvent(VoiceUiEvent.Start)
            runCurrent()

            viewModel.onEvent(VoiceUiEvent.Stop)
            runCurrent()

            assertEquals(1, speechInput.released)
            assertEquals(VoiceUiState.Idle, viewModel.state.value)
        }

    @Test
    fun `clearing view model cancels active speech session`() =
        runTest(dispatcher.scheduler) {
            val speechInput = HoldingSpeechInput()
            val viewModel = VoiceInputViewModel(speechInput)
            val store = ViewModelStore().apply { put("voice", viewModel) }
            viewModel.onEvent(VoiceUiEvent.Start)
            runCurrent()

            store.clear()
            runCurrent()

            assertEquals(1, speechInput.released)
        }

    @Test
    fun `consuming a different result id leaves current result intact`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Final("привет")) }
            val viewModel = VoiceInputViewModel(speechInput)
            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()
            val result = viewModel.state.value as VoiceUiState.Result

            viewModel.onEvent(VoiceUiEvent.ResultConsumed(result.id + 1))

            assertEquals(result, viewModel.state.value)
        }

    @Test
    fun `result ids increase across sessions`() =
        runTest(dispatcher.scheduler) {
            val speechInput =
                FakeSpeechInput().apply {
                    script(SpeechEvent.Final("первый"))
                    script(SpeechEvent.Final("второй"))
                }
            val viewModel = VoiceInputViewModel(speechInput)
            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()
            val firstId = (viewModel.state.value as VoiceUiState.Result).id
            viewModel.onEvent(VoiceUiEvent.ResultConsumed(firstId))

            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()
            val secondId = (viewModel.state.value as VoiceUiState.Result).id

            assertNotEquals(firstId, secondId)
        }

    @Test
    fun `start after error begins a new session`() =
        runTest(dispatcher.scheduler) {
            val speechInput =
                FakeSpeechInput().apply {
                    script(SpeechEvent.Failed(SpeechFailure.NO_MATCH))
                    script(SpeechEvent.Final("повтор"))
                }
            val viewModel = VoiceInputViewModel(speechInput)
            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()
            assertEquals(VoiceUiState.Error(SpeechFailure.NO_MATCH), viewModel.state.value)

            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()

            assertEquals("повтор", (viewModel.state.value as VoiceUiState.Result).text)
            assertEquals(2, speechInput.requests.size)
        }

    @Test
    fun `speech request uses Russian and allows online recognition`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Final("готово")) }
            val viewModel = VoiceInputViewModel(speechInput)
            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()

            assertEquals(SpeechRequest("ru-RU", offlineOnly = false), speechInput.requests.single())
        }

    @Test
    fun `busy response becomes an error state`() =
        runTest(dispatcher.scheduler) {
            val speechInput = FakeSpeechInput().apply { script(SpeechEvent.Failed(SpeechFailure.BUSY)) }
            val viewModel = VoiceInputViewModel(speechInput)
            viewModel.onEvent(VoiceUiEvent.Start)
            advanceUntilIdle()

            assertEquals(VoiceUiState.Error(SpeechFailure.BUSY), viewModel.state.value)
        }

    private class HoldingSpeechInput : SpeechInput {
        val requests = mutableListOf<SpeechRequest>()
        var released = 0
            private set

        override fun listen(request: SpeechRequest): Flow<SpeechEvent> =
            flow {
                requests += request
                emit(SpeechEvent.Listening)
                try {
                    awaitCancellation()
                } finally {
                    released += 1
                }
            }
    }
}
