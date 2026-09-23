package com.example.phoneagent.ui.settings

import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AppSignInMethod
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.domain.conversation.HistoryItem
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.ConversationRepository
import com.example.phoneagent.domain.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun `sign out delegates to the authentication coordinator`() =
        runTest(dispatcher.scheduler) {
            val authCoordinator = FakeAuthCoordinator(AppAuthStatus.SignedIn(AuthMethod.API_KEY))
            val viewModel =
                SettingsViewModel(
                    authCoordinator = authCoordinator,
                    settingsRepository = RecordingSettingsRepository(AppSettings()),
                    modelCatalog = FakeModelCatalog(),
                    conversationRepository = FakeConversationRepository(),
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.SignOut)
            advanceUntilIdle()

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
                )
            advanceUntilIdle()

            viewModel.onEvent(SettingsUiEvent.ClearHistory)
            advanceUntilIdle()

            assertEquals(listOf(emptyList<HistoryItem>()), conversationRepository.replacements)
        }

    private class RecordingSettingsRepository(
        private var current: AppSettings,
    ) : SettingsRepository {
        val savedSettings = mutableListOf<AppSettings>()

        override suspend fun load(): AppSettings = current

        override suspend fun save(settings: AppSettings) {
            savedSettings += settings
            current = settings
        }
    }

    private class FakeModelCatalog(
        private val result: ModelCatalogResult = ModelCatalogResult.Available(listOf("gpt-5.6-luna", "gpt-5.6-terra")),
    ) : ModelCatalog {
        override suspend fun load(): ModelCatalogResult = result
    }

    private class FakeAuthCoordinator(
        initial: AppAuthStatus = AppAuthStatus.SignedOut(),
    ) : AuthCoordinator {
        override val state = MutableStateFlow(initial)
        var signOutCalls = 0

        override suspend fun restore() = Unit

        override suspend fun signInWithBrowser() = Unit

        override suspend fun signInWithDeviceCode() = Unit

        override suspend fun signInWithApiKey(key: String) = Unit

        override suspend fun signOut() {
            signOutCalls += 1
        }

        override fun openDeviceVerificationPage() = Unit
    }

    private class FakeConversationRepository : ConversationRepository {
        val replacements = mutableListOf<List<HistoryItem>>()

        override suspend fun load(): List<HistoryItem> = emptyList()

        override suspend fun replace(items: List<HistoryItem>) {
            replacements += items
        }

        override fun observe(): Flow<List<HistoryItem>> = flowOf(emptyList())
    }
}
