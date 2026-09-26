package com.example.phoneagent.screenshot

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.ui.about.AboutScreen
import com.example.phoneagent.ui.about.LicenseKind
import com.example.phoneagent.ui.about.LicenseTextResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class AboutScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val licenseTexts by lazy { LicenseTextResources.readAll(context.resources) }

    @Test
    fun `about screen light and dark smoke`() =
        composeRule.snapshot(
            "about_screen",
            variants = listOf(Variant.Light, Variant.Dark),
            fillScreen = true,
        ) {
            AboutScreen(
                versionName = "1.2.3",
                licenseTexts = licenseTexts,
                expandedLicenseKinds = emptySet(),
                onToggleLicense = {},
                onBack = {},
            )
        }

    @Test
    fun `expanded license text smoke`() =
        composeRule.snapshot(
            "about_screen_expanded_apache",
            variants = listOf(Variant.Light, Variant.Dark),
            fillScreen = true,
        ) {
            AboutScreen(
                versionName = "1.2.3",
                licenseTexts = licenseTexts,
                expandedLicenseKinds = setOf(LicenseKind.APACHE_2_0),
                onToggleLicense = {},
                onBack = {},
            )
        }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class AboutScreenNarrowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val licenseTexts by lazy { LicenseTextResources.readAll(context.resources) }

    @Test
    fun `about screen narrow large font smoke`() =
        composeRule.snapshot(
            "about_screen_narrow",
            variants = listOf(Variant.LargeFont),
            fillScreen = true,
        ) {
            AboutScreen(
                versionName = "1.2.3",
                licenseTexts = licenseTexts,
                expandedLicenseKinds = emptySet(),
                onToggleLicense = {},
                onBack = {},
            )
        }
}
