package com.example.phoneagent.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AppNavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `back from About returns to Settings and then Chat`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AppNavigationHost(
                    chatContent = { onOpenSettings ->
                        Column {
                            Text("Chat")
                            Text("Open settings", Modifier.clickable(onClick = onOpenSettings))
                        }
                    },
                    settingsContent = { _, onOpenAbout ->
                        Column {
                            Text("Settings")
                            Text("Open about", Modifier.clickable(onClick = onOpenAbout))
                        }
                    },
                    aboutContent = {
                        Text("About")
                    },
                )
            }
        }

        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.onNodeWithText("Open about").performClick()

        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Chat").assertDoesNotExist()

        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chat").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
    }
}
