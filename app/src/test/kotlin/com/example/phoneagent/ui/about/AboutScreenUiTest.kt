package com.example.phoneagent.ui.about

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AboutScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val licenseTexts by lazy { LicenseTextResources.readAll(context.resources) }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    private fun show(
        versionName: String = "1.2.3",
        expandedLicenseKinds: Set<LicenseKind> = emptySet(),
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AboutScreen(
                    versionName = versionName,
                    licenseTexts = licenseTexts,
                    expandedLicenseKinds = expandedLicenseKinds,
                    onToggleLicense = {},
                    onBack = onBack,
                )
            }
        }
    }

    @Test
    fun `screen shows app name version and local data notice`() {
        show(versionName = "9.8.7")

        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_version, "9.8.7")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_local_data)).assertIsDisplayed()
    }

    @Test
    fun `back button has an accessible name and invokes callback`() {
        var backCalls = 0
        show(onBack = { backCalls++ })

        composeRule.onNodeWithContentDescription(string(R.string.go_back)).assertIsDisplayed().performClick()

        assertEquals(1, backCalls)
    }

    @Test
    fun `every catalog entry is shown with its exact license name`() {
        show()
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)

        LicenseCatalog.entries.forEach { entry ->
            list.performScrollToNode(hasText(entry.name))
            composeRule.onNodeWithTag("about-entry-${entry.name}").assertIsDisplayed()
            val licenseName = string(LicenseTextResources.nameResourceIds.getValue(entry.license))
            val licenseNode = composeRule.onNodeWithTag("about-license-${entry.name}")
            licenseNode.performScrollTo()
            licenseNode.assert(hasText(string(R.string.about_license_name, licenseName)))
            licenseNode.assertIsDisplayed()
        }
    }

    @Test
    fun `apache text is collapsed by default and toggles with a state description`() {
        var expanded by mutableStateOf(emptySet<LicenseKind>())
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AboutScreen(
                    versionName = "1.2.3",
                    licenseTexts = licenseTexts,
                    expandedLicenseKinds = expanded,
                    onToggleLicense = { kind ->
                        expanded = if (kind in expanded) expanded - kind else expanded + kind
                    },
                    onBack = {},
                )
            }
        }
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)
        val licenseName = string(R.string.license_name_apache_2_0)
        val title = string(R.string.about_license_text_title, licenseName)
        val showLabel = string(R.string.about_show_license, licenseName)
        val hideLabel = string(R.string.about_hide_license, licenseName)
        val textMarker = "Version 2.0, January 2004"
        val endMarker = "limitations under the License."
        list.performScrollToNode(hasText(title))
        val toggle = composeRule.onNodeWithText(showLabel)

        toggle.assertIsDisplayed()
        assertEquals(string(R.string.about_collapsed_state), toggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        composeRule.onNodeWithText(textMarker, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(endMarker, substring = true).assertDoesNotExist()

        toggle.performClick()
        composeRule.onNodeWithText(textMarker, substring = true).assertExists()
        composeRule.onNodeWithText(endMarker, substring = true).assertExists()
        val hideToggle = composeRule.onNodeWithText(hideLabel)
        assertEquals(string(R.string.about_expanded_state), hideToggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        hideToggle.performClick()
        composeRule.onNodeWithText(textMarker, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(endMarker, substring = true).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `expanded license survives saved state restoration`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            AppTheme(useDynamicColor = false) {
                AboutRoute(onBack = {})
            }
        }
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)
        val licenseName = string(R.string.license_name_apache_2_0)
        val title = string(R.string.about_license_text_title, licenseName)
        list.performScrollToNode(hasText(title))
        composeRule.onNodeWithText(string(R.string.about_show_license, licenseName)).performClick()
        composeRule.onNodeWithText("Version 2.0, January 2004", substring = true).assertExists()
        composeRule.onNodeWithText("limitations under the License.", substring = true).assertExists()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG).performScrollToNode(hasText(title))
        composeRule.onNodeWithText("Version 2.0, January 2004", substring = true).assertExists()
        composeRule.onNodeWithText("limitations under the License.", substring = true).assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class AboutScreenNarrowLargeFontUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val licenseTexts by lazy { LicenseTextResources.readAll(context.resources) }

    @Test
    fun `320dp at 200 percent keeps header and first section visible and reaches the final entry`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme(useDynamicColor = false) {
                    AboutScreen(
                        versionName = "1.2.3",
                        licenseTexts = licenseTexts,
                        expandedLicenseKinds = emptySet(),
                        onToggleLicense = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.about_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.go_back)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_section_application)).assertIsDisplayed()
        val lastEntry = LicenseCatalog.entries.last()
        composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG).performScrollToNode(hasText(lastEntry.name))
        composeRule.onNodeWithTag("about-entry-${lastEntry.name}").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.go_back)).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h800dp-land-mdpi")
class AboutScreenWideUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `content column does not exceed 640dp`() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                AboutScreen(
                    versionName = "1.2.3",
                    licenseTexts = LicenseTextResources.readAll(context.resources),
                    expandedLicenseKinds = emptySet(),
                    onToggleLicense = {},
                    onBack = {},
                )
            }
        }

        val width =
            composeRule
                .onNodeWithTag(ABOUT_APPLICATION_SECTION_TAG)
                .fetchSemanticsNode()
                .boundsInRoot.width

        assertTrue("content width $width must not exceed 640dp", width <= 640f)
    }
}
