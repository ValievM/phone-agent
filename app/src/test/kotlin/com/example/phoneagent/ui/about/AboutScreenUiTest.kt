package com.example.phoneagent.ui.about

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.test.onRoot
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
                    onOpenProject = {},
                    onBack = onBack,
                )
            }
        }
    }

    private fun showRoute(context: Context) {
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                AppTheme(useDynamicColor = false) {
                    AboutRoute(onBack = {})
                }
            }
        }
    }

    @Test
    fun `screen shows app identity and grouped open-source sections`() {
        show(versionName = "9.8.7")

        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_version, "9.8.7")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_summary)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_local_data)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_section_fonts)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_section_icons)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_section_libraries)).assertIsDisplayed()
    }

    @Test
    fun `back button has an accessible name and invokes callback`() {
        var backCalls = 0
        show(onBack = { backCalls++ })

        composeRule.onNodeWithContentDescription(string(R.string.go_back)).assertIsDisplayed().performClick()

        assertEquals(1, backCalls)
    }

    @Test
    fun `catalog entries show compact license badges and complete holder names`() {
        show()
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)

        LicenseCatalog.entries.forEach { entry ->
            list.performScrollToNode(hasText(entry.name))
            composeRule
                .onNodeWithTag("about-entry-${entry.name}")
                .assert(hasText(entry.name))
                .assertIsDisplayed()
            composeRule
                .onNodeWithTag("about-license-badge-${entry.name}-0")
                .assert(hasText(string(LicenseTextResources.badgeNameResourceIds.getValue(entry.license))))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule
                .onNodeWithTag("about-entry-${entry.name}-holder-0")
                .assert(hasText(string(R.string.about_rights_holder, entry.holder)))
                .performScrollTo()
                .assertIsDisplayed()
            entry.additionalNotices.forEachIndexed { index, notice ->
                composeRule
                    .onNodeWithTag("about-license-badge-${entry.name}-${index + 1}")
                    .assert(hasText(string(LicenseTextResources.badgeNameResourceIds.getValue(notice.license))))
                    .performScrollTo()
                    .assertIsDisplayed()
                composeRule
                    .onNodeWithTag("about-entry-${entry.name}-holder-${index + 1}")
                    .assert(hasText(string(R.string.about_rights_holder, notice.holder)))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
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
                    onOpenProject = {},
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
        toggle.performScrollTo()

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

    @Test
    fun `MIT notice expands to its copyright and permission terms`() {
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
                    onOpenProject = {},
                    onBack = {},
                )
            }
        }
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)
        val licenseName = string(R.string.license_name_mit)
        val title = string(R.string.about_license_text_title, licenseName)
        list.performScrollToNode(hasText(title))
        composeRule.onNodeWithText(string(R.string.about_show_license, licenseName)).performClick()

        composeRule.onNodeWithText("Copyright (c) 2021 Erik Hellman", substring = true).assertExists()
        composeRule.onNodeWithText("THE SOFTWARE IS PROVIDED \"AS IS\"", substring = true).assertExists()
    }

    @Test
    fun `Boost text section expands to the source copyright and disclaimer`() {
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
                    onOpenProject = {},
                    onBack = {},
                )
            }
        }
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)
        val licenseName = string(R.string.license_name_boost_1_0)
        val title = string(R.string.about_license_text_title, licenseName)
        list.performScrollToNode(hasText(title))
        composeRule.onNodeWithText(string(R.string.about_show_license, licenseName)).performClick()

        composeRule.onNodeWithText("Boost Software License - Version 1.0", substring = true).assertExists()
        composeRule.onNodeWithText("Permission is hereby granted", substring = true).assertExists()
        composeRule.onNodeWithText("DEALINGS IN THE SOFTWARE.", substring = true).assertExists()
    }

    @Test
    fun `project link requires domain confirmation and cancel leaves browser unopened`() {
        val recordingContext = RecordingContext(ApplicationProvider.getApplicationContext())
        showRoute(recordingContext)
        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)
        list.performScrollToNode(hasText("Inter"))

        composeRule.onNodeWithText("https://github.com/rsms/inter", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Проект Inter").performClick()
        composeRule.onNodeWithText("github.com").assertIsDisplayed()
        assertTrue("the project link must not open before approval", recordingContext.startedActivities.isEmpty())

        composeRule.onNodeWithText(string(R.string.link_cancel_action)).performClick()

        assertTrue("cancel must leave the browser unopened", recordingContext.startedActivities.isEmpty())
    }

    @Test
    fun `confirming a project link launches the confirmed HTTPS URL`() {
        val recordingContext = RecordingContext(ApplicationProvider.getApplicationContext())
        showRoute(recordingContext)
        composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG).performScrollToNode(hasText("Inter"))

        composeRule.onNodeWithContentDescription("Проект Inter").performClick()
        composeRule.onNodeWithText(string(R.string.link_open_action)).performClick()

        assertEquals(1, recordingContext.startedActivities.size)
        assertEquals(Intent.ACTION_VIEW, recordingContext.startedActivities.single().action)
        assertEquals(Uri.parse("https://github.com/rsms/inter"), recordingContext.startedActivities.single().data)
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
        composeRule.onNodeWithText(string(R.string.about_show_license, licenseName)).performScrollTo().performClick()
        composeRule.onNodeWithText("Version 2.0, January 2004", substring = true).assertExists()
        composeRule.onNodeWithText("limitations under the License.", substring = true).assertExists()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG).performScrollToNode(hasText(title))
        composeRule.onNodeWithText("Version 2.0, January 2004", substring = true).assertExists()
        composeRule.onNodeWithText("limitations under the License.", substring = true).assertExists()
    }
}

private class RecordingContext(
    base: Context,
) : ContextWrapper(base) {
    val startedActivities = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
        startedActivities += intent
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
    fun `320dp at 200 percent wraps holder and license text within the viewport`() {
        var expanded by mutableStateOf(emptySet<LicenseKind>())
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme(useDynamicColor = false) {
                    AboutScreen(
                        versionName = "1.2.3",
                        licenseTexts = licenseTexts,
                        expandedLicenseKinds = expanded,
                        onToggleLicense = { kind -> expanded = if (kind in expanded) expanded - kind else expanded + kind },
                        onOpenProject = {},
                        onBack = {},
                    )
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = root.width / 320f
        composeRule.onNodeWithText(context.getString(R.string.about_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.go_back)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_summary)).assertIsDisplayed()

        val list = composeRule.onNodeWithTag(ABOUT_CONTENT_LIST_TAG)
        val longestHolderEntry = LicenseCatalog.entries.maxBy { it.holder.length }
        list.performScrollToNode(hasText(longestHolderEntry.name))
        val holderNode = composeRule.onNodeWithText(context.getString(R.string.about_rights_holder, longestHolderEntry.holder))
        holderNode.performScrollTo()
        val holderBounds = holderNode.fetchSemanticsNode().boundsInRoot
        assertTrue("the longest holder must wrap instead of being ellipsized", holderBounds.height / density > 64f)
        assertTrue("holder begins outside the viewport: $holderBounds", holderBounds.left >= root.left)
        assertTrue("holder exceeds the viewport: $holderBounds", holderBounds.right <= root.right)

        val licenseName = context.getString(R.string.license_name_apache_2_0)
        list.performScrollToNode(hasText(context.getString(R.string.about_license_text_title, licenseName)))
        composeRule
            .onNodeWithText(context.getString(R.string.about_show_license, licenseName))
            .performScrollTo()
            .performClick()
        val licenseText = composeRule.onNodeWithText("limitations under the License.", substring = true)
        licenseText.performScrollTo()
        val licenseBounds = licenseText.fetchSemanticsNode().boundsInRoot
        assertTrue("license text begins outside the viewport: $licenseBounds", licenseBounds.left >= root.left)
        assertTrue("license text exceeds the viewport: $licenseBounds", licenseBounds.right <= root.right)

        val lastEntry = LicenseCatalog.entries.last()
        list.performScrollToNode(hasText(lastEntry.name))
        composeRule.onNodeWithText(lastEntry.name).assertIsDisplayed()
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
                    onOpenProject = {},
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
