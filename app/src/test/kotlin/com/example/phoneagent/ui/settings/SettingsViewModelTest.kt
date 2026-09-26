package com.example.phoneagent.ui.settings

import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AppSignInMethod
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.RedactingAppLogger
import com.example.phoneagent.domain.platform.SanitizedThrowable
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selecting model saves it through settings repository`() =
        runTest(dispatcher.scheduler) {
            val initialSettings =
                AppSettings(
                    useDynamicColors = false,
                    useDarkTheme = true,
                    model = "gpt-5.6-luna",
                    searchFolderUri = "content://example/tree/current",
                )
            val settingsRepository = RecordingSettingsRepository(initialSettings)
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = settingsRepository,
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                    logger = FakeAppLogger(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertEquals(listOf(initialSettings.copy(model = "gpt-5.6-terra")), settingsRepository.savedSettings)
        }

    @Test
    fun `signed in state exposes the authentication method`() =
        runTest(dispatcher.scheduler) {
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(AppAuthStatus.SignedIn(AuthMethod.API_KEY)),
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                    logger = FakeAppLogger(),
                )
            advanceUntilIdle()

            assertEquals(AppSignInMethod.ApiKey, viewModel.state.value.signInMethod)
        }

    @Test
    fun `unavailable model catalog is exposed without clearing saved model`() =
        runTest(dispatcher.scheduler) {
            val savedModel = "gpt-5.6-luna"
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = RecordingSettingsRepository(AppSettings(model = savedModel)),
                    modelCatalog = FakeModelCatalog(ModelCatalogResult.Unavailable),
                    conversationRepository = FakeConversationRepository(),
                    logger = FakeAppLogger(),
                )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.modelCatalogUnavailable)
            assertFalse(
                viewModel.state.value.availableModels
                    .isNotEmpty(),
            )
            assertEquals(savedModel, viewModel.state.value.model)
        }

    @Test
    fun `sign out without deleting history delegates to the authentication coordinator`() =
        runTest(dispatcher.scheduler) {
            val authCoordinator = FakeAuthCoordinator(AppAuthStatus.SignedIn(AuthMethod.API_KEY))
            val conversationRepository = FakeConversationRepository()
            val viewModel =
                SettingsViewModel(
                    authCoordinator = authCoordinator,
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = conversationRepository,
                    logger = FakeAppLogger(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SignOut(clearHistory = false))
            advanceUntilIdle()

            assertEquals(1, authCoordinator.signOutCalls)
            assertTrue(authCoordinator.state.value is AppAuthStatus.SignedOut)
            assertEquals(0, conversationRepository.replaceCalls)
        }

    @Test
    fun `sign out clears history before signing out when requested`() =
        runTest(dispatcher.scheduler) {
            val operations = mutableListOf<String>()
            val authCoordinator =
                FakeAuthCoordinator(
                    initial = AppAuthStatus.SignedIn(AuthMethod.API_KEY),
                    operations = operations,
                )
            val conversationRepository = FakeConversationRepository(operations)
            val viewModel =
                viewModelWith(
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    authCoordinator = authCoordinator,
                    conversationRepository = conversationRepository,
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SignOut(clearHistory = true))
            advanceUntilIdle()

            assertEquals(listOf("clearHistory", "signOut"), operations)
            assertEquals(listOf(emptyList<HistoryItem>()), conversationRepository.replacements)
        }

    @Test
    fun `failed history deletion keeps the account signed in`() =
        runTest(dispatcher.scheduler) {
            val operations = mutableListOf<String>()
            val authCoordinator =
                FakeAuthCoordinator(
                    initial = AppAuthStatus.SignedIn(AuthMethod.API_KEY),
                    operations = operations,
                )
            val conversationRepository =
                FakeConversationRepository(operations).apply { replaceFailure = IOException("history unavailable") }
            val viewModel =
                viewModelWith(
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    authCoordinator = authCoordinator,
                    conversationRepository = conversationRepository,
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SignOut(clearHistory = true))
            advanceUntilIdle()

            assertEquals(listOf("clearHistory"), operations)
            assertEquals(0, authCoordinator.signOutCalls)
            assertTrue(authCoordinator.state.value is AppAuthStatus.SignedIn)
            assertTrue(viewModel.state.value.historyClearUnavailable)
        }

    @Test
    fun `repeated sign out while history is being deleted starts only once`() =
        runTest(dispatcher.scheduler) {
            val operations = mutableListOf<String>()
            val authCoordinator =
                FakeAuthCoordinator(
                    initial = AppAuthStatus.SignedIn(AuthMethod.API_KEY),
                    operations = operations,
                )
            val conversationRepository =
                FakeConversationRepository(operations).apply { replaceGate = CompletableDeferred() }
            val viewModel =
                viewModelWith(
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    authCoordinator = authCoordinator,
                    conversationRepository = conversationRepository,
                )
            advanceUntilIdle()

            val event = SettingsUiEvent.SignOut(clearHistory = true)
            viewModel.onEvent(event)
            runCurrent()
            viewModel.onEvent(event)
            runCurrent()

            assertEquals(1, conversationRepository.replaceCalls)
            assertEquals(0, authCoordinator.signOutCalls)
            conversationRepository.replaceGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("clearHistory", "signOut"), operations)
            assertEquals(1, authCoordinator.signOutCalls)
        }

    @Test
    fun `selecting a search folder persists its uri and preserves other settings`() =
        runTest(dispatcher.scheduler) {
            val initialSettings = AppSettings(model = "gpt-5.6-luna")
            val settingsRepository = RecordingSettingsRepository(initialSettings)
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = settingsRepository,
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                    logger = FakeAppLogger(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SaveSearchFolder("content://example/tree/selected"))
            advanceUntilIdle()

            assertEquals(
                listOf(initialSettings.copy(searchFolderUri = "content://example/tree/selected")),
                settingsRepository.savedSettings,
            )
        }

    @Test
    fun `clear history replaces the persisted snapshot with an empty list`() =
        runTest(dispatcher.scheduler) {
            val conversationRepository = FakeConversationRepository()
            val viewModel =
                SettingsViewModel(
                    authCoordinator = FakeAuthCoordinator(),
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = conversationRepository,
                    logger = FakeAppLogger(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.ClearHistory)
            advanceUntilIdle()

            assertEquals(listOf(emptyList<HistoryItem>()), conversationRepository.replacements)
        }

    @Test
    fun `the stored theme choices are exposed once settings are loaded`() =
        runTest(dispatcher.scheduler) {
            val viewModel = viewModelWith(RecordingSettingsRepository(AppSettings(useDarkTheme = false, useDynamicColors = true)))
            advanceUntilIdle()

            assertEquals(ThemeMode.LIGHT, viewModel.state.value.themeMode)
            assertEquals(true, viewModel.state.value.useSystemColors)
        }

    @Test
    fun `choosing a theme mode saves the matching dark theme flag and updates the state`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.SYSTEM))
            advanceUntilIdle()

            assertEquals(listOf(true, false, null), repository.savedSettings.map { it.useDarkTheme })
            assertEquals(ThemeMode.SYSTEM, viewModel.state.value.themeMode)
        }

    @Test
    fun `switching system colors saves the choice and updates the state`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SetSystemColors(true))
            advanceUntilIdle()

            assertEquals(listOf(true), repository.savedSettings.map { it.useDynamicColors })
            assertEquals(true, viewModel.state.value.useSystemColors)
        }

    @Test
    fun `a failed theme save keeps the previous choice and reports it`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings(useDarkTheme = true))
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()

            assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
            assertTrue(viewModel.state.value.themeSaveFailed)
        }

    @Test
    fun `a failed model save is not reported as a theme error`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true

            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.themeSaveFailed)
            assertTrue(viewModel.state.value.settingsUnavailable)
        }

    @Test
    fun `a later successful save clears the theme error`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            repository.failSaves = false
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.themeSaveFailed)
            assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
        }

    @Test
    fun `a failed save does not change what the next save is based on`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings(useDarkTheme = null, model = "gpt-5.6-luna"))
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()
            repository.failSaves = true
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()
            repository.failSaves = false

            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertEquals(listOf(AppSettings(useDarkTheme = null, model = "gpt-5.6-terra")), repository.savedSettings)
        }

    @Test
    fun `two quick theme choices are saved in order and the last one wins`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()

            assertEquals(listOf(true, false), repository.savedSettings.map { it.useDarkTheme })
            assertEquals(ThemeMode.LIGHT, viewModel.state.value.themeMode)
        }

    @Test
    fun `nothing is written after the settings failed to load, so model and folder are never overwritten with defaults`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings(model = "gpt-5.6-luna", searchFolderUri = "content://tree/1"))
            repository.failLoad = true
            val viewModel = viewModelWith(repository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            viewModel.onEvent(SettingsUiEvent.SetSystemColors(true))
            viewModel.onEvent(SettingsUiEvent.SelectModel("gpt-5.6-terra"))
            advanceUntilIdle()

            assertTrue(repository.savedSettings.isEmpty())
            assertTrue(viewModel.state.value.settingsLoadFailed)
            assertFalse(viewModel.state.value.canEditSettings)
        }

    @Test
    fun `nothing is written while the settings are still loading`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val viewModel = viewModelWith(repository)
            assertFalse(viewModel.state.value.canEditSettings)

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertTrue(repository.savedSettings.isEmpty())
            assertTrue(viewModel.state.value.canEditSettings)
        }

    @Test
    fun `a cancelled save is not reported as a failure or logged`() =
        runTest(dispatcher.scheduler) {
            val repository = RecordingSettingsRepository(AppSettings())
            val logger = FakeAppLogger()
            val viewModel = viewModelWith(repository, logger = logger)
            advanceUntilIdle()
            repository.cancelSaves = true

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.themeSaveFailed)
            assertFalse(viewModel.state.value.settingsUnavailable)
            assertTrue(logger.events.isEmpty())
        }

    @Test
    fun `failed settings load logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val repository = RecordingSettingsRepository(AppSettings()).apply { loadFailure = IOException(SECRET_PATH) }
            viewModelWith(repository, logger = RedactingAppLogger(records))

            advanceUntilIdle()

            assertSafeWarning(records, "load settings failed")
        }

    @Test
    fun `failed sign out logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val authCoordinator = FakeAuthCoordinator().apply { signOutFailure = IOException(SECRET_PATH) }
            val viewModel =
                viewModelWith(
                    RecordingSettingsRepository(AppSettings()),
                    logger = RedactingAppLogger(records),
                    authCoordinator = authCoordinator,
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SignOut(clearHistory = false))
            advanceUntilIdle()

            assertSafeWarning(records, "sign out failed")
        }

    @Test
    fun `failed settings save logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val repository = RecordingSettingsRepository(AppSettings()).apply { saveFailure = IOException(SECRET_PATH) }
            val viewModel = viewModelWith(repository, logger = RedactingAppLogger(records))
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SelectThemeMode(ThemeMode.DARK))
            advanceUntilIdle()

            assertSafeWarning(records, "save settings failed")
        }

    @Test
    fun `failed history clear logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val conversationRepository = FakeConversationRepository().apply { replaceFailure = IOException(SECRET_PATH) }
            val viewModel =
                viewModelWith(
                    RecordingSettingsRepository(AppSettings()),
                    logger = RedactingAppLogger(records),
                    conversationRepository = conversationRepository,
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.ClearHistory)
            advanceUntilIdle()

            assertSafeWarning(records, "clear conversation history failed")
        }

    @Test
    fun `failed model catalog load logs a safe warning`() =
        runTest(dispatcher.scheduler) {
            val records = FakeAppLogger()
            val modelCatalog = FakeModelCatalog(failure = IOException(SECRET_PATH))
            viewModelWith(RecordingSettingsRepository(AppSettings()), logger = RedactingAppLogger(records), modelCatalog = modelCatalog)

            advanceUntilIdle()

            assertSafeWarning(records, "load model catalog failed")
        }

    private fun assertSafeWarning(
        records: FakeAppLogger,
        operation: String,
    ) {
        assertEquals(1, records.events.size)
        val event = records.events.single()
        assertEquals(LogLevel.WARN, event.level)
        assertEquals("SettingsViewModel", event.tag)
        assertEquals(operation, event.message)
        assertFalse(event.toString().contains(SECRET_PATH))
        assertEquals("IOException", event.fields["exceptionType"])
        assertTrue(event.throwable is SanitizedThrowable)
    }

    private companion object {
        const val SECRET_PATH = "secret-path"
    }

    private fun viewModelWith(
        settingsRepository: SettingsRepository,
        logger: AppLogger = FakeAppLogger(),
        authCoordinator: AuthCoordinator = FakeAuthCoordinator(),
        modelCatalog: ModelCatalog = FakeModelCatalog(),
        conversationRepository: ConversationRepository = FakeConversationRepository(),
    ) = SettingsViewModel(
        authCoordinator = authCoordinator,
        settingsRepository = settingsRepository,
        modelCatalog = modelCatalog,
        conversationRepository = conversationRepository,
        logger = logger,
    )

    private class RecordingSettingsRepository(
        private var current: AppSettings,
    ) : SettingsRepository {
        val savedSettings = mutableListOf<AppSettings>()
        var failSaves = false
        var failLoad = false
        var cancelSaves = false
        var loadFailure: IOException? = null
        var saveFailure: IOException? = null

        override suspend fun load(): AppSettings {
            loadFailure?.let { throw it }
            if (failLoad) throw java.io.IOException("cannot read settings")
            return current
        }

        override suspend fun save(settings: AppSettings) {
            if (cancelSaves) throw kotlinx.coroutines.CancellationException("save cancelled")
            saveFailure?.let { throw it }
            if (failSaves) throw java.io.IOException("cannot write settings")
            savedSettings += settings
            current = settings
        }
    }

    private class FakeModelCatalog(
        private val result: ModelCatalogResult = ModelCatalogResult.Available(listOf("gpt-5.6-luna", "gpt-5.6-terra")),
        private val failure: IOException? = null,
    ) : ModelCatalog {
        override suspend fun load(): ModelCatalogResult {
            failure?.let { throw it }
            return result
        }
    }

    private class FakeAuthCoordinator(
        initial: AppAuthStatus = AppAuthStatus.SignedOut(),
        private val operations: MutableList<String> = mutableListOf(),
    ) : AuthCoordinator {
        override val state = MutableStateFlow(initial)
        var signOutCalls = 0
        var signOutFailure: IOException? = null

        override suspend fun restore() = Unit

        override suspend fun signInWithBrowser() = Unit

        override suspend fun signInWithDeviceCode() = Unit

        override suspend fun signInWithApiKey(key: String) = Unit

        override suspend fun signOut() {
            operations += "signOut"
            signOutCalls += 1
            signOutFailure?.let { throw it }
            state.value = AppAuthStatus.SignedOut()
        }

        override fun openDeviceVerificationPage() = Unit
    }

    private class FakeConversationRepository(
        private val operations: MutableList<String> = mutableListOf(),
    ) : ConversationRepository {
        val replacements = mutableListOf<List<HistoryItem>>()
        var replaceFailure: IOException? = null
        var replaceCalls = 0
        var replaceGate: CompletableDeferred<Unit>? = null

        override suspend fun load(): List<HistoryItem> = emptyList()

        override suspend fun replace(items: List<HistoryItem>) {
            replaceCalls += 1
            operations += "clearHistory"
            replaceGate?.await()
            replaceFailure?.let { throw it }
            replacements += items
        }

        override fun observe(): Flow<List<HistoryItem>> = flowOf(emptyList())
    }
}
