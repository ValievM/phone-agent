package com.example.phoneagent.ui.designsystem.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class ToolCallCardStatusUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every status shows its words icon and role colors in both themes at 320dp`() {
        val cases = statusCases(context)
        var currentCase by mutableStateOf(cases.first())
        var darkTheme by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        var densityScale = 1f
        var actualContainer = Color.Unspecified
        var actualContent = Color.Unspecified
        var expectedContainer = Color.Unspecified
        var expectedContent = Color.Unspecified
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, fontScale)) {
                AppTheme(darkTheme = darkTheme, useDynamicColor = false) {
                    val colorScheme = MaterialTheme.colorScheme
                    val extended = AppTheme.extendedColors
                    val expectedLook =
                        when (currentCase.role) {
                            StatusColorRole.Warning -> extended.warningContainer to extended.onWarningContainer
                            StatusColorRole.Primary -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
                            StatusColorRole.Success -> extended.successContainer to extended.onSuccessContainer
                            StatusColorRole.Error -> colorScheme.errorContainer to colorScheme.onErrorContainer
                            StatusColorRole.Neutral -> Color.Transparent to colorScheme.onSurfaceVariant
                        }
                    val actualLook = currentCase.status.look()
                    val density = LocalDensity.current
                    SideEffect {
                        expectedContainer = expectedLook.first
                        expectedContent = expectedLook.second
                        actualContainer = actualLook.container
                        actualContent = actualLook.content
                        densityScale = density.density
                    }
                    ToolCallCard(
                        state = currentCase.status,
                        description = CallDescription(title = "", summary = ""),
                        arguments = buildJsonObject {},
                        expanded = false,
                        onExpandToggle = {},
                    )
                }
            }
        }

        for (dark in listOf(false, true)) {
            for (scale in listOf(1f, 2f)) {
                for (case in cases) {
                    composeRule.runOnIdle {
                        currentCase = case
                        darkTheme = dark
                        fontScale = scale
                    }
                    composeRule.waitForIdle()
                    composeRule.mainClock.advanceTimeBy(400)
                    composeRule.waitForIdle()

                    try {
                        composeRule.onNodeWithText(case.label).assertIsDisplayed()
                    } catch (failure: AssertionError) {
                        throw AssertionError("Status ${case.status} has no visible label in dark=$dark, fontScale=$scale", failure)
                    }
                    val announcement = composeRule.onNodeWithText(case.label).fetchSemanticsNode().config
                    assertFalse("The decorative icon must not add a content description", announcement.contains(SemanticsProperties.ContentDescription))

                    val statusNode = composeRule.onNodeWithTag(TOOL_CALL_STATUS_TEST_TAG, useUnmergedTree = true)
                    val statusBounds = statusNode.fetchSemanticsNode().boundsInRoot
                    assertTrue("${case.label} starts outside the 320dp viewport: $statusBounds", statusBounds.left >= 0f)
                    assertTrue(
                        "${case.label} exceeds the 320dp viewport: $statusBounds",
                        statusBounds.right <= 320f * densityScale + 1f,
                    )

                    val iconNode = composeRule.onNodeWithTag(TOOL_CALL_STATUS_ICON_TEST_TAG, useUnmergedTree = true)
                    iconNode.assertIsDisplayed()
                    assertEquals("${case.label} has the wrong container role in dark=$dark", expectedContainer, actualContainer)
                    assertEquals("${case.label} has the wrong content role in dark=$dark", expectedContent, actualContent)
                }
            }
        }
    }

    @Test
    fun `long handoff target is sanitized truncated and wrapped at 320dp and 200 percent`() {
        val target = "Ча\u0000сы" + "🚀".repeat(45)
        val expectedTarget = "Часы" + "🚀".repeat(36)
        val expectedLongLabel = context.getString(R.string.tool_call_status_handed_off, expectedTarget)
        val shortLabel = context.getString(R.string.tool_call_status_handed_off, "Часы")
        var status by mutableStateOf<ToolCallStatus>(ToolCallStatus.HandedOff("Часы"))
        var darkTheme by mutableStateOf(false)
        var densityScale = 1f

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, 2f)) {
                AppTheme(darkTheme = darkTheme, useDynamicColor = false) {
                    densityScale = LocalDensity.current.density
                    ToolCallCard(
                        state = status,
                        description = CallDescription(title = "", summary = ""),
                        arguments = buildJsonObject {},
                        expanded = false,
                        onExpandToggle = {},
                    )
                }
            }
        }

        for (dark in listOf(false, true)) {
            composeRule.runOnIdle {
                darkTheme = dark
                status = ToolCallStatus.HandedOff("Часы")
            }
            composeRule.waitForIdle()
            val shortBounds = composeRule.onNodeWithTag(TOOL_CALL_STATUS_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            composeRule.onNodeWithText(shortLabel).assertIsDisplayed()

            composeRule.runOnIdle { status = ToolCallStatus.HandedOff(target) }
            composeRule.waitForIdle()
            val longNode = composeRule.onNodeWithText(expectedLongLabel)
            longNode.assertIsDisplayed()
            val longBounds = composeRule.onNodeWithTag(TOOL_CALL_STATUS_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("The long status did not wrap: short=$shortBounds long=$longBounds", longBounds.height > shortBounds.height)
            assertTrue("The long status exceeds the 320dp viewport: $longBounds", longBounds.right <= 320f * densityScale + 1f)
        }
    }

    private fun statusCases(context: Context) =
        listOf(
            StatusCase(ToolCallStatus.AwaitingApproval, context.getString(R.string.tool_call_status_awaiting), StatusColorRole.Warning),
            StatusCase(ToolCallStatus.Running, context.getString(R.string.tool_call_status_running), StatusColorRole.Primary),
            StatusCase(ToolCallStatus.Done("12:30"), "12:30", StatusColorRole.Success),
            StatusCase(ToolCallStatus.FilesFound(2), context.getString(R.string.file_search_completed, 2), StatusColorRole.Success),
            StatusCase(ToolCallStatus.HandedOff("Часы"), context.getString(R.string.tool_call_status_handed_off, "Часы"), StatusColorRole.Primary),
            StatusCase(ToolCallStatus.NotShown, context.getString(R.string.tool_call_status_not_shown), StatusColorRole.Warning),
            StatusCase(ToolCallStatus.Failed("Ошибка"), "Ошибка", StatusColorRole.Error),
            StatusCase(ToolCallStatus.Rejected, context.getString(R.string.tool_call_status_rejected), StatusColorRole.Neutral),
            StatusCase(ToolCallStatus.Cancelled, context.getString(R.string.tool_call_status_cancelled), StatusColorRole.Neutral),
        )

    private data class StatusCase(
        val status: ToolCallStatus,
        val label: String,
        val role: StatusColorRole,
    )

    private enum class StatusColorRole {
        Warning,
        Primary,
        Success,
        Error,
        Neutral,
    }
}

