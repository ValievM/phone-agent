package com.example.phoneagent.ui.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

private val themeModes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

private fun themeModeLabel(mode: ThemeMode): String {
    val resource =
        when (mode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        }
    return ApplicationProvider.getApplicationContext<Context>().getString(resource)
}

@androidx.compose.runtime.Composable
private fun AppearanceTestContent(
    selectedMode: ThemeMode,
    fontScale: Float = 1f,
    onEvent: (SettingsUiEvent) -> Unit = {},
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        AppTheme(useDynamicColor = false) {
            SettingsScreen(
                state = SettingsUiState(isLoading = false, themeMode = selectedMode),
                onEvent = onEvent,
                onChooseFolder = {},
                onBack = {},
                aboutEntry = SettingsAboutEntry(versionName = "1.2.3", onOpen = {}),
            )
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AppearanceResponsiveStandardUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme mode segments have equal width and height at standard width`() {
        composeRule.setContent { AppearanceTestContent(ThemeMode.SYSTEM) }
        composeRule.waitForIdle()

        val bounds =
            themeModes.map { mode ->
                composeRule
                    .onNodeWithText(themeModeLabel(mode))
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
            }

        themeModes.forEach { mode -> composeRule.onNodeWithText(themeModeLabel(mode)).assertIsDisplayed() }
        assertEquals("mode segment widths must match", bounds.first().width, bounds[1].width, 2f)
        assertEquals("mode segment widths must match", bounds.first().width, bounds[2].width, 2f)
        assertEquals("mode segment heights must match", bounds.first().height, bounds[1].height, 2f)
        assertEquals("mode segment heights must match", bounds.first().height, bounds[2].height, 2f)
        assertEquals("theme choices must share one horizontal row", bounds[0].center.y, bounds[1].center.y, 2f)
        assertEquals("theme choices must share one horizontal row", bounds[0].center.y, bounds[2].center.y, 2f)
    }

    @Test
    fun `semantics show exactly the saved theme mode as selected`() {
        var selectedMode by mutableStateOf(ThemeMode.SYSTEM)
        composeRule.setContent { AppearanceTestContent(selectedMode) }
        composeRule.waitForIdle()

        themeModes.forEach { selected ->
            composeRule.runOnIdle { selectedMode = selected }
            composeRule.waitForIdle()

            themeModes.forEach { mode ->
                val option = composeRule.onNodeWithText(themeModeLabel(mode))
                if (mode == selected) option.assertIsSelected() else option.assertIsNotSelected()
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h891dp-port-xxhdpi")
class AppearanceResponsiveNarrowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `narrow 200 percent text stacks without wrapping or horizontal overflow`() {
        composeRule.setContent { AppearanceTestContent(ThemeMode.SYSTEM, fontScale = 2f) }
        composeRule.waitForIdle()

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val labels =
            themeModes.map { mode ->
                composeRule.onNodeWithText(themeModeLabel(mode), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
        val density = root.width / 320f

        assertTrue(
            "theme choices should form one vertical list",
            labels[0].center.y < labels[1].center.y && labels[1].center.y < labels[2].center.y,
        )
        labels.forEach { bounds ->
            assertTrue("label begins outside the viewport: $bounds", bounds.left >= root.left)
            assertTrue("label exceeds the viewport: $bounds", bounds.right <= root.right)
            assertTrue("label wrapped at 200% font scale: $bounds", bounds.height < 64f * density)
        }
    }

    @Test
    fun `narrow radio choices keep selected semantics and report the chosen mode`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeRule.setContent { AppearanceTestContent(ThemeMode.DARK, fontScale = 2f, onEvent = events::add) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(themeModeLabel(ThemeMode.DARK)).assertIsSelected()
        composeRule.onNodeWithText(themeModeLabel(ThemeMode.SYSTEM)).assertIsNotSelected()
        composeRule.onNodeWithText(themeModeLabel(ThemeMode.LIGHT)).assertIsNotSelected()
        composeRule.onNodeWithText(themeModeLabel(ThemeMode.LIGHT)).performClick()

        assertEquals(listOf(SettingsUiEvent.SelectThemeMode(ThemeMode.LIGHT)), events)
    }
}
