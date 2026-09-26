package com.example.phoneagent.ui.root

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.RedactingAppLogger
import com.example.phoneagent.domain.platform.SanitizedThrowable
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the state is Loading until the first settings arrive`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()

            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed

            assertEquals(ThemeState.Loading, viewModel.state.value)
        }

    @Test
    fun `the first emission makes the state Ready`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed

            repository.emit(AppSettings(useDarkTheme = true))
            advanceUntilIdle()

            assertEquals(ThemeState.Ready(AppSettings(useDarkTheme = true)), viewModel.state.value)
        }

    @Test
    fun `a later change of the saved theme reaches the state without a restart`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed
            repository.emit(AppSettings(useDarkTheme = null))
            advanceUntilIdle()

            repository.emit(AppSettings(useDarkTheme = false))
            advanceUntilIdle()

            assertEquals(ThemeState.Ready(AppSettings(useDarkTheme = false)), viewModel.state.value)
        }

    @Test
    fun `a read failure logs a safe warning and makes the state unavailable`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val records = FakeAppLogger()
            val viewModel = ThemeViewModel(repository, RedactingAppLogger(records))
            advanceUntilIdle() // the VM is now subscribed

            repository.fail(IOException(SECRET_PATH))
            advanceUntilIdle()

            assertEquals(ThemeState.Unavailable(lastKnown = null), viewModel.state.value)
            assertSafeWarning(records)
        }

    @Test
    fun `a read failure after Ready keeps the last known settings`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed
            repository.emit(AppSettings(useDarkTheme = true))
            advanceUntilIdle()

            repository.fail(IOException("disk error"))
            advanceUntilIdle()

            assertEquals(ThemeState.Unavailable(lastKnown = AppSettings(useDarkTheme = true)), viewModel.state.value)
        }

    @Test
    fun `retry after a failure subscribes again and becomes Ready`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed
            repository.fail(IOException("disk error"))
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()
            repository.emit(AppSettings(useDarkTheme = false))
            advanceUntilIdle()

            assertEquals(ThemeState.Ready(AppSettings(useDarkTheme = false)), viewModel.state.value)
            assertEquals(2, repository.subscriptions)
        }

    @Test
    fun `retry while settings are fine does not subscribe a second time`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed
            repository.emit(AppSettings())
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(1, repository.subscriptions)
        }

    @Test
    fun `a failure is never turned into default settings and nothing is written`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val viewModel = ThemeViewModel(repository, logger = FakeAppLogger())
            advanceUntilIdle() // the VM is now subscribed
            repository.fail(IOException("disk error"))
            advanceUntilIdle()

            assertNull((viewModel.state.value as ThemeState.Unavailable).lastKnown)
            assertEquals(0, repository.saveCalls)
        }

    @Test
    fun `cancellation of the read is not reported or logged`() =
        runTest(dispatcher.scheduler) {
            val repository = ScriptedSettings()
            val logger = FakeAppLogger()
            val viewModel = ThemeViewModel(repository, logger)
            advanceUntilIdle() // the VM is now subscribed

            repository.fail(CancellationException(SECRET_PATH))
            advanceUntilIdle()

            assertEquals(ThemeState.Loading, viewModel.state.value)
            assertTrue(logger.events.isEmpty())
        }

    private fun assertSafeWarning(records: FakeAppLogger) {
        assertEquals(1, records.events.size)
        val event = records.events.single()
        assertEquals(LogLevel.WARN, event.level)
        assertEquals("ThemeViewModel", event.tag)
        assertEquals("observe theme settings failed", event.message)
        assertFalse(event.toString().contains(SECRET_PATH))
        assertEquals("IOException", event.fields["exceptionType"])
        assertTrue(event.throwable is SanitizedThrowable)
    }

    private companion object {
        const val SECRET_PATH = "secret-path"
    }

    /** A repository whose emissions and failures the test drives by hand. */
    private class ScriptedSettings : SettingsRepository {
        private val events = MutableSharedFlow<Result<AppSettings>>(extraBufferCapacity = 16, replay = 0)
        var subscriptions = 0
            private set
        var saveCalls = 0
            private set

        suspend fun emit(settings: AppSettings) = events.emit(Result.success(settings))

        suspend fun fail(error: Throwable) = events.emit(Result.failure(error))

        override suspend fun load(): AppSettings = throw UnsupportedOperationException("the root reads through observe()")

        override fun observe(): Flow<AppSettings> =
            flow {
                subscriptions++
                events.collect { result -> emit(result.getOrThrow()) }
            }

        override suspend fun save(settings: AppSettings) {
            saveCalls++
        }
    }
}
