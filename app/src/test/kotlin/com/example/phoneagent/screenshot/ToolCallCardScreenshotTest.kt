package com.example.phoneagent.screenshot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.ui.designsystem.components.ToolCallCard
import com.example.phoneagent.ui.designsystem.components.ToolCallStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_QUALIFIERS)
class ToolCallCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val timeDescription = CallDescription(title = "Текущее время", summary = "Местное время")

    private fun card(
        name: String,
        status: ToolCallStatus,
        expanded: Boolean = false,
        variants: List<Variant> = listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
        description: CallDescription = alarmDescription,
        arguments: JsonObject = alarmArguments,
    ) = composeRule.snapshot("tool_call_card_$name", variants) {
        ToolCallCard(
            state = status,
            description = description,
            arguments = arguments,
            expanded = expanded,
            onExpandToggle = {},
            fileHits = fileHits,
        )
    }

    @Test
    fun awaitingApproval() = card("awaiting", ToolCallStatus.AwaitingApproval)

    @Test
    fun running() = card("running", ToolCallStatus.Running)

    @Test
    fun done() =
        card(
            "done",
            ToolCallStatus.Done("12:30"),
            description = timeDescription,
            arguments = buildJsonObject {},
        )

    @Test
    fun handedOff() = card("handed_off", ToolCallStatus.HandedOff("Часы"))

    @Test
    fun handedOffLongTarget() =
        card(
            "handed_off_long_target",
            ToolCallStatus.HandedOff("Будильники и таймеры на телефоне, приложение Часы"),
        )

    @Test
    fun filesFound() = card("files_found", ToolCallStatus.FilesFound(fileHits.size), expanded = true)

    @Test
    fun failed() = card("failed", ToolCallStatus.Failed("Приложение «Часы» не найдено"))

    @Test
    fun notShown() = card("not_shown", ToolCallStatus.NotShown)

    @Test
    fun rejected() = card("rejected", ToolCallStatus.Rejected)

    @Test
    fun cancelled() = card("cancelled", ToolCallStatus.Cancelled)

    @Test
    fun expandedLargeFont() =
        card(
            "expanded",
            ToolCallStatus.Done("12:30"),
            expanded = true,
            variants = listOf(Variant.Light, Variant.Dark, Variant.LargeFont),
            description = timeDescription,
            arguments = buildJsonObject {},
        )
}
