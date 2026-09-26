package com.example.phoneagent.ui.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeConversationRepository
import com.example.phoneagent.domain.model.ModelCatalog
import com.example.phoneagent.domain.model.ModelCatalogResult
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.storage.AppSettings
import com.example.phoneagent.domain.storage.SettingsRepository
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h891dp-port-xxhdpi")
class SearchFolderSettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `long saved folder name is sanitized and fits narrow high-font layout`() {
        val uri = installProvider("Te\u0000legram" + "🚀".repeat(45))
        val viewModel = viewModel(AppSettings(searchFolderUri = uri.toString()))
        show(viewModel)

        val expectedName = "Telegram" + "🚀".repeat(32)
        val expectedLabel = "Выбрана папка «$expectedName»."
        scrollTo(expectedLabel, viewModel)

        val label = composeRule.onNodeWithText(expectedLabel)
        label.assertIsDisplayed()
        assertTrue(
            "The selected folder name exceeds the 320dp viewport",
            label.fetchSemanticsNode().boundsInRoot.right <= 320f * composeRule.density.density + 1f,
        )
    }

    @Test
    fun `selected folder name comes from SAF document display name`() {
        val uri = installProvider("Project Notes")
        val viewModel = viewModel(AppSettings(searchFolderUri = uri.toString()))
        show(viewModel)

        val expectedLabel = "Выбрана папка «Project Notes»."
        scrollTo(expectedLabel, viewModel)

        composeRule.onNodeWithText(expectedLabel).assertIsDisplayed()
    }

    @Test
    fun `unavailable saved folder shows fallback without crashing`() {
        val uri = installProvider("unused", failQuery = true)
        val viewModel = viewModel(AppSettings(searchFolderUri = uri.toString()))
        show(viewModel)

        scrollTo("Папка выбрана", viewModel)

        composeRule.onNodeWithText("Папка выбрана").assertIsDisplayed()
    }

    @Test
    fun `cancelling folder picker keeps the saved folder`() {
        val uri = installProvider("Telegram")
        val repository = RecordingSettingsRepository(AppSettings(searchFolderUri = uri.toString()))
        val viewModel = viewModel(repository)
        val registry = HoldingActivityResultRegistry()
        show(viewModel, registry)
        scrollTo("Изменить папку", viewModel)
        composeRule.onNodeWithTag(SETTINGS_CONTENT_LIST_TAG).performScrollToNode(hasText("Изменить папку"))

        composeRule.onNodeWithText("Изменить папку").performClick()
        composeRule.waitUntil(5_000) { registry.requestCode != null }
        composeRule.runOnIdle {
            registry.dispatchResult(checkNotNull(registry.requestCode), null as Uri?)
        }
        composeRule.waitForIdle()

        assertEquals(uri.toString(), repository.current.searchFolderUri)
        assertTrue(repository.savedSettings.isEmpty())
    }

    @Test
    fun `Downloads guidance is visible before selection and after activity recreation`() {
        val viewModel = viewModel(AppSettings())
        show(viewModel)
        val guidance = "Android не разрешает выбрать «Загрузки» целиком. Выберите другую папку или вложенную в неё."

        scrollTo(guidance, viewModel)
        val guidanceNode = composeRule.onNodeWithText(guidance)
        guidanceNode.assertIsDisplayed()
        val chooseFolderButton = composeRule.onNodeWithText("Выбрать папку")
        chooseFolderButton.assertIsDisplayed()
        assertTrue(
            "Downloads guidance must follow the folder picker action",
            chooseFolderButton.fetchSemanticsNode().boundsInRoot.bottom < guidanceNode.fetchSemanticsNode().boundsInRoot.top,
        )
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                AppTheme(useDynamicColor = false) {
                    SettingsRoute(
                        onBack = {},
                        onOpenAbout = {},
                        viewModel = viewModel,
                        dispatcherProvider = DefaultDispatcherProvider,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        scrollTo(guidance, viewModel)

        composeRule.onNodeWithText(guidance).assertIsDisplayed()
    }

    private fun installProvider(
        name: String,
        failQuery: Boolean = false,
    ): Uri {
        FolderDocumentProvider.displayName = name
        FolderDocumentProvider.failQuery = failQuery
        Robolectric.setupContentProvider(FolderDocumentProvider::class.java, TEST_AUTHORITY)
        return Uri.parse("content://$TEST_AUTHORITY/tree/root-id")
    }

    private fun show(
        viewModel: SettingsViewModel,
        registry: ActivityResultRegistry? = null,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 2f)) {
                val screen: @Composable () -> Unit = {
                    AppTheme(useDynamicColor = false) {
                        SettingsRoute(
                            onBack = {},
                            onOpenAbout = {},
                            viewModel = viewModel,
                            dispatcherProvider = DefaultDispatcherProvider,
                        )
                    }
                }
                if (registry == null) {
                    screen()
                } else {
                    val owner =
                        object : ActivityResultRegistryOwner {
                            override val activityResultRegistry: ActivityResultRegistry = registry
                        }
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { screen() }
                }
            }
        }
    }

    private fun scrollTo(
        text: String,
        viewModel: SettingsViewModel,
    ) {
        composeRule.waitUntil(5_000) { !viewModel.state.value.isLoading }
        val list = composeRule.onNodeWithTag(SETTINGS_CONTENT_LIST_TAG)
        list.performScrollToNode(hasText("Папка для поиска файлов"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun viewModel(initialSettings: AppSettings): SettingsViewModel = viewModel(RecordingSettingsRepository(initialSettings))

    private fun viewModel(repository: RecordingSettingsRepository): SettingsViewModel =
        SettingsViewModel(
            authCoordinator = FakeAuthCoordinator(),
            settingsRepository = repository,
            modelCatalog =
                object : ModelCatalog {
                    override suspend fun load(): ModelCatalogResult = ModelCatalogResult.Available(listOf("gpt-5.6-luna"))
                },
            conversationRepository = FakeConversationRepository(),
            logger = FakeAppLogger(),
        )

    private class RecordingSettingsRepository(
        var current: AppSettings,
    ) : SettingsRepository {
        val savedSettings = mutableListOf<AppSettings>()

        override suspend fun load(): AppSettings = current

        override suspend fun save(settings: AppSettings) {
            current = settings
            savedSettings += settings
        }
    }

    private class FakeAuthCoordinator : AuthCoordinator {
        override val state = MutableStateFlow<AppAuthStatus>(AppAuthStatus.SignedOut())

        override suspend fun restore() = Unit

        override suspend fun signInWithBrowser() = Unit

        override suspend fun signInWithDeviceCode() = Unit

        override suspend fun signInWithApiKey(key: String) = Unit

        override suspend fun signOut() = Unit

        override fun openDeviceVerificationPage() = Unit
    }

    private class HoldingActivityResultRegistry : ActivityResultRegistry() {
        var requestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            this.requestCode = requestCode
        }
    }

    private class FolderDocumentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            if (failQuery) throw SecurityException("permission revoked")
            return MatrixCursor(arrayOf("display_name")).apply { addRow(arrayOf(displayName)) }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        companion object {
            var displayName: String = ""
            var failQuery: Boolean = false
        }
    }

    private companion object {
        const val TEST_AUTHORITY = "com.example.phoneagent.folder-picker-test"
    }
}
