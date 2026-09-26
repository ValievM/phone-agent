package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.settings.SignOutConfirmationSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class SignOutConfirmationScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signOutConfirmationInBothThemesAndLargeFont() =
        composeRule.snapshot(
            "settings_sign_out_confirmation",
            listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
            wholeScreen = true,
            fillScreen = true,
        ) {
            SignOutConfirmationSheet(onDeleteHistory = {}, onKeepHistory = {}, onDismiss = {})
        }
}
