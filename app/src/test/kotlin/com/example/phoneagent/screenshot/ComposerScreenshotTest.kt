package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.Composer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ComposerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idle() =
        composeRule.snapshot("composer_idle", listOf(Variant.Light, Variant.Dark, Variant.LargeFont)) {
            Composer(enabled = true, isRunning = false, onSubmit = {}, onStop = {})
        }

    @Test
    fun running() =
        composeRule.snapshot("composer_running") {
            Composer(enabled = false, isRunning = true, onSubmit = {}, onStop = {})
        }

    @Test
    fun unavailable() =
        composeRule.snapshot("composer_unavailable") {
            Composer(enabled = false, isRunning = false, onSubmit = {}, onStop = {})
        }
}

internal const val PHONE_QUALIFIERS = "w411dp-h891dp-port-xxhdpi"
