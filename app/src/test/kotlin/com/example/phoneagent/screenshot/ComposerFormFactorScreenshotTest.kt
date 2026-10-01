package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.designsystem.components.Composer
import com.example.phoneagent.ui.designsystem.components.ComposerMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class NarrowComposerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `voice composer fits narrow phone at 200 percent font scale`() =
        composeRule.snapshot("composer_voice_narrow_font200", listOf(Variant.LargeFont), fillScreen = true) {
            Composer(
                draft = "",
                onDraftChange = {},
                mode = ComposerMode.Ready,
                onSubmit = {},
                onStop = {},
                voice =
                    com.example.phoneagent.ui.designsystem.components
                        .ComposerVoice(false, {}, {}),
            )
        }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
class LandscapeComposerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `voice composer fits in landscape`() =
        composeRule.snapshot("composer_voice_landscape", listOf(Variant.Light), fillScreen = true) {
            Composer(
                draft = "",
                onDraftChange = {},
                mode = ComposerMode.Ready,
                onSubmit = {},
                onStop = {},
                voice =
                    com.example.phoneagent.ui.designsystem.components
                        .ComposerVoice(false, {}, {}),
            )
        }
}
