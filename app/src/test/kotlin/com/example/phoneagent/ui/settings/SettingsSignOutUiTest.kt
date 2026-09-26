package com.example.phoneagent.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class SettingsSignOutUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<SettingsUiEvent>()

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Composable
    private fun SettingsContent() {
        AppTheme(useDynamicColor = false) {
            SettingsScreen(
                state = SettingsUiState(isLoading = false, isLoadingModels = false, isSignedIn = true),
                onEvent = { event -> events.add(event) },
                onChooseFolder = {},
                onBack = {},
                aboutEntry = SettingsAboutEntry(versionName = "0.1.0", onOpen = {}),
            )
        }
    }

    private fun showScreen() = composeRule.setContent { SettingsContent() }

    private fun clickSignOut() = composeRule.onNodeWithText(string(R.string.sign_out)).performClick()

    private fun bounds(node: SemanticsNodeInteraction): Rect = node.fetchSemanticsNode().boundsInRoot

    @Test
    fun `opening sign out choices shows three actions without sending an event`() {
        showScreen()

        clickSignOut()

        composeRule.onNodeWithText("Выйти из аккаунта?").assertIsDisplayed()
        composeRule.onNodeWithText("Выйти и удалить историю").assertIsDisplayed()
        composeRule.onNodeWithText("Выйти, историю оставить").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.file_cancel_action)).assertIsDisplayed()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `each sign out choice reports its action and cancel reports none`() {
        showScreen()

        clickSignOut()
        composeRule.onNodeWithText("Выйти, историю оставить").performClick()
        assertEquals(listOf(SettingsUiEvent.SignOut(clearHistory = false)), events)

        events.clear()
        clickSignOut()
        composeRule.onNodeWithText("Выйти и удалить историю").performClick()
        assertEquals(listOf(SettingsUiEvent.SignOut(clearHistory = true)), events)

        events.clear()
        clickSignOut()
        composeRule.onNodeWithText(string(R.string.file_cancel_action)).performClick()
        assertTrue(events.isEmpty())
        composeRule.onNodeWithText("Выйти из аккаунта?").assertDoesNotExist()
    }

    @Test
    fun `all actions fit on a narrow screen at two hundred percent font size`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SettingsContent()
            }
        }
        clickSignOut()

        val delete = composeRule.onNodeWithText("Выйти и удалить историю")
        val keep = composeRule.onNodeWithText("Выйти, историю оставить")
        val cancel = composeRule.onNodeWithText(string(R.string.file_cancel_action))
        delete.assertIsDisplayed()
        keep.assertIsDisplayed()
        cancel.assertIsDisplayed()

        val deleteBounds = bounds(delete)
        val keepBounds = bounds(keep)
        val cancelBounds = bounds(cancel)
        assertTrue("actions must be stacked", deleteBounds.bottom <= keepBounds.top && keepBounds.bottom <= cancelBounds.top)
        delete.performClick()
        assertEquals(listOf(SettingsUiEvent.SignOut(clearHistory = true)), events)
        events.clear()
        clickSignOut()
        keep.performClick()
        assertEquals(listOf(SettingsUiEvent.SignOut(clearHistory = false)), events)

        events.clear()
        clickSignOut()
        cancel.performClick()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `sign out choice sheet survives saved state restoration`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { SettingsContent() }
        clickSignOut()
        composeRule.onNodeWithText("Выйти из аккаунта?").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Выйти из аккаунта?").assertIsDisplayed()
        composeRule.onNodeWithText("Выйти и удалить историю").assertIsDisplayed()
        assertTrue(events.isEmpty())
    }
}
