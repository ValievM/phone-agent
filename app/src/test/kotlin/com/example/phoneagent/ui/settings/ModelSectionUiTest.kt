package com.example.phoneagent.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ModelSectionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<SettingsUiEvent>()

    @Test
    fun `multiple available models appear with the default option`() {
        val models = listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna")
        show(catalogState(models))
        scrollToModelSection()

        val defaultLabel = string(R.string.settings_model_default)
        composeRule.onNodeWithText(defaultLabel).performClick()

        composeRule.onAllNodesWithText(defaultLabel).assertCountEquals(2)
        models.forEach { model -> composeRule.onNodeWithText(model).assertIsDisplayed() }
        composeRule.onNodeWithText(models.first()).performClick()

        assertEquals(listOf(SettingsUiEvent.SelectModel(models.first())), events)
    }

    @Test
    fun `one available model is static and cannot open a menu`() {
        val model = "gpt-5.6-luna"
        show(catalogState(listOf(model)))
        scrollToModelSection()

        composeRule.onNodeWithText("Модель: $model (единственная доступная)").assertIsDisplayed()

        composeRule.onAllNodesWithText(string(R.string.settings_model_default)).assertCountEquals(0)
    }

    @Test
    fun `empty model catalog explains why selection is unavailable`() {
        show(catalogState(emptyList()))
        scrollToModelSection()

        composeRule.onNodeWithText("Список моделей пуст. Выбор недоступен.").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_model_default)).assertIsNotEnabled()
    }

    @Test
    fun `unavailable model catalog keeps selection disabled`() {
        show(catalogState(emptyList()).copy(modelCatalogUnavailable = true))
        scrollToModelSection()

        composeRule.onNodeWithText(string(R.string.settings_models_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_model_default)).assertIsNotEnabled()
    }

    @Test
    fun `loading model catalog keeps selection disabled`() {
        show(catalogState(emptyList()).copy(isLoadingModels = true))
        scrollToModelSection()

        composeRule.onNodeWithText(string(R.string.settings_models_loading)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_model_default)).assertIsNotEnabled()
    }

    private fun show(state: SettingsUiState) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                SettingsScreen(
                    state = state,
                    onEvent = { events += it },
                    onChooseFolder = {},
                    onBack = {},
                    aboutEntry = SettingsAboutEntry(versionName = "test", onOpen = {}),
                )
            }
        }
    }

    private fun scrollToModelSection() {
        composeRule
            .onNodeWithTag(SETTINGS_CONTENT_LIST_TAG)
            .performScrollToNode(hasText(string(R.string.settings_model_title)))
    }

    private fun catalogState(models: List<String>) =
        SettingsUiState(
            isLoading = false,
            availableModels = models,
            isLoadingModels = false,
        )

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
