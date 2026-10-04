package com.example.phoneagent.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.AssistantMessage
import com.example.phoneagent.ui.designsystem.components.AssistantMessageActions
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AssistantAnswerActionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `copy and share actions fit a narrow reply at large font size`() {
        composeRule.snapshot(
            "assistant_copy_share_narrow",
            listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
            immediateWork = true,
        ) {
            Column(Modifier.width(320.dp).padding(AppTheme.spacing.medium)) {
                AssistantMessage("**Проверенный ответ**\n\nТекст с `кодом`.")
            }
        }
    }

    @Test
    fun `copied confirmation is captured`() {
        composeRule.snapshot("assistant_copy_share_copied", listOf(Variant.Light, Variant.Dark)) {
            Column(Modifier.padding(AppTheme.spacing.medium)) {
                AssistantMessageActions(copied = true, onCopy = {}, onShare = {})
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h320dp-land-xxhdpi")
class AssistantAnswerActionsLandscapeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `copy and share actions render in landscape`() {
        composeRule.snapshot("assistant_copy_share_landscape", immediateWork = true) {
            Column(Modifier.padding(AppTheme.spacing.medium)) {
                AssistantMessage("**Проверенный ответ**\n\nТекст с `кодом`.")
            }
        }
    }
}
