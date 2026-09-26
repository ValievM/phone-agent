package com.example.phoneagent.ui.designsystem.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.phoneagent.ui.designsystem.icons.AppIcons
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
        val state = StatusTestState(cases.first())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { StatusTestContent(state) }

        val configurations =
            listOf(
                StatusTestConfiguration(darkTheme = false, fontScale = 1f),
                StatusTestConfiguration(darkTheme = false, fontScale = 2f),
                StatusTestConfiguration(darkTheme = true, fontScale = 1f),
                StatusTestConfiguration(darkTheme = true, fontScale = 2f),
            )
        for (configuration in configurations) {
            verifyStatusCases(cases, state, configuration)
        }
    }

    private fun verifyStatusCases(
        cases: List<StatusCase>,
        state: StatusTestState,
        configuration: StatusTestConfiguration,
    ) {
        for (case in cases) {
            composeRule.runOnIdle {
                state.currentCase = case
                state.darkTheme = configuration.darkTheme
                state.fontScale = configuration.fontScale
            }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.waitForIdle()
            assertStatusCase(case, state)
        }
    }

    private fun assertStatusCase(
        case: StatusCase,
        state: StatusTestState,
    ) {
        composeRule.onNodeWithText(case.label).assertIsDisplayed()
        val announcement = composeRule.onNodeWithText(case.label).fetchSemanticsNode().config
        assertFalse("The decorative icon must not add a content description", announcement.contains(SemanticsProperties.ContentDescription))

        val statusNode = composeRule.onNodeWithTag(TOOL_CALL_STATUS_TEST_TAG, useUnmergedTree = true)
        val statusBounds = statusNode.fetchSemanticsNode().boundsInRoot
        assertTrue("${case.label} starts outside the 320dp viewport: $statusBounds", statusBounds.left >= 0f)
        assertTrue(
            "${case.label} exceeds the 320dp viewport: $statusBounds",
            statusBounds.right <= 320f * state.densityScale + 1f,
        )

        composeRule.onNodeWithTag(TOOL_CALL_STATUS_ICON_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            "${case.label} has the wrong container role in dark=${state.darkTheme}",
            state.expectedContainer,
            state.actualContainer,
        )
        assertEquals(
            "${case.label} has the wrong content role in dark=${state.darkTheme}",
            state.expectedContent,
            state.actualContent,
        )
    }

    @Composable
    private fun StatusTestContent(state: StatusTestState) {
        val deviceDensity = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, state.fontScale)) {
            AppTheme(darkTheme = state.darkTheme, useDynamicColor = false) {
                val colorScheme = MaterialTheme.colorScheme
                val extended = AppTheme.extendedColors
                val expectedLook =
                    when (state.currentCase.role) {
                        StatusColorRole.Warning -> extended.warningContainer to extended.onWarningContainer
                        StatusColorRole.Primary -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
                        StatusColorRole.Success -> extended.successContainer to extended.onSuccessContainer
                        StatusColorRole.Error -> colorScheme.errorContainer to colorScheme.onErrorContainer
                        StatusColorRole.Neutral -> Color.Transparent to colorScheme.onSurfaceVariant
                    }
                val actualLook = state.currentCase.status.look()
                val density = LocalDensity.current
                SideEffect {
                    state.expectedContainer = expectedLook.first
                    state.expectedContent = expectedLook.second
                    state.actualContainer = actualLook.container
                    state.actualContent = actualLook.content
                    state.densityScale = density.density
                }
                ToolCallCard(
                    state = state.currentCase.status,
                    description = CallDescription(title = "", summary = ""),
                    arguments = buildJsonObject {},
                    expanded = false,
                    onExpandToggle = {},
                )
            }
        }
    }

    @Test
    fun `handoff and not shown map to exact send and warning icons`() {
        assertEquals(
            AppIcons.send,
            ToolCallStatus.HandedOff("Часы").iconResourceId(),
        )
        assertEquals(AppIcons.warning, ToolCallStatus.NotShown.iconResourceId())
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
            val shortBounds =
                composeRule
                    .onNodeWithTag(TOOL_CALL_STATUS_TEST_TAG, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            composeRule.onNodeWithText(shortLabel).assertIsDisplayed()

            composeRule.runOnIdle { status = ToolCallStatus.HandedOff(target) }
            composeRule.waitForIdle()
            val longNode = composeRule.onNodeWithText(expectedLongLabel)
            longNode.assertIsDisplayed()
            val longBounds =
                composeRule
                    .onNodeWithTag(TOOL_CALL_STATUS_TEST_TAG, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertTrue(
                "The long status did not wrap: short=$shortBounds long=$longBounds",
                longBounds.height > shortBounds.height,
            )
            assertTrue(
                "The long status exceeds the 320dp viewport: $longBounds",
                longBounds.right <= 320f * densityScale + 1f,
            )
        }
    }

    private fun statusCases(context: Context) =
        listOf(
            StatusCase(
                status = ToolCallStatus.AwaitingApproval,
                label = context.getString(R.string.tool_call_status_awaiting),
                role = StatusColorRole.Warning,
            ),
            StatusCase(
                status = ToolCallStatus.Running,
                label = context.getString(R.string.tool_call_status_running),
                role = StatusColorRole.Primary,
            ),
            StatusCase(
                status = ToolCallStatus.Done("12:30"),
                label = "12:30",
                role = StatusColorRole.Success,
            ),
            StatusCase(
                status = ToolCallStatus.FilesFound(2),
                label = context.getString(R.string.file_search_completed, 2),
                role = StatusColorRole.Success,
            ),
            StatusCase(
                status = ToolCallStatus.HandedOff("Часы"),
                label = context.getString(R.string.tool_call_status_handed_off, "Часы"),
                role = StatusColorRole.Primary,
            ),
            StatusCase(
                status = ToolCallStatus.NotShown,
                label = context.getString(R.string.tool_call_status_not_shown),
                role = StatusColorRole.Warning,
            ),
            StatusCase(
                status = ToolCallStatus.Failed("Ошибка"),
                label = "Ошибка",
                role = StatusColorRole.Error,
            ),
            StatusCase(
                status = ToolCallStatus.Failed("tool execution timed out"),
                label = "Инструмент не успел выполниться",
                role = StatusColorRole.Error,
            ),
            StatusCase(
                status = ToolCallStatus.Rejected,
                label = context.getString(R.string.tool_call_status_rejected),
                role = StatusColorRole.Neutral,
            ),
            StatusCase(
                status = ToolCallStatus.Cancelled,
                label = context.getString(R.string.tool_call_status_cancelled),
                role = StatusColorRole.Neutral,
            ),
        )

    private data class StatusCase(
        val status: ToolCallStatus,
        val label: String,
        val role: StatusColorRole,
    )

    private data class StatusTestConfiguration(
        val darkTheme: Boolean,
        val fontScale: Float,
    )

    private class StatusTestState(
        initialCase: StatusCase,
    ) {
        var currentCase by mutableStateOf(initialCase)
        var darkTheme by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        var densityScale = 1f
        var actualContainer = Color.Unspecified
        var actualContent = Color.Unspecified
        var expectedContainer = Color.Unspecified
        var expectedContent = Color.Unspecified
    }

    private enum class StatusColorRole {
        Warning,
        Primary,
        Success,
        Error,
        Neutral,
    }
}
