package com.example.phoneagent.tools.alarm

import com.example.phoneagent.domain.tools.EditableField
import com.example.phoneagent.domain.tools.EditableFieldKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class AlarmArgumentEditorTest {
    private val tool = AlarmTool(NoopPort, 1_000.milliseconds, TestTimeSource())

    @Test
    fun `alarm editor exposes hour minute and message fields`() {
        val editor = requireNotNull(tool.argumentEditor(args(12, 30)))

        assertEquals(
            listOf(
                EditableField("hour", "Час", EditableFieldKind.INTEGER),
                EditableField("minute", "Минуты", EditableFieldKind.INTEGER),
                EditableField("message", "Название", EditableFieldKind.TEXT),
            ),
            editor.fields,
        )
    }

    @Test
    fun `valid edits update the confirmation summary`() {
        val editor = requireNotNull(tool.argumentEditor(args(12, 30)))

        val review = editor.review(args(7, 5, "Работа"))

        assertEquals(null, review.validationError)
        assertEquals("На 07:05. Название: Работа", review.description.summary)
    }

    @Test
    fun `hour outside range is rejected`() {
        val editor = requireNotNull(tool.argumentEditor(args(12, 30)))

        assertEquals(AlarmCopy.VALIDATION_HOUR_RANGE, editor.review(args(24, 30)).validationError)
    }

    @Test
    fun `minute outside range is rejected`() {
        val editor = requireNotNull(tool.argumentEditor(args(12, 30)))

        assertEquals(AlarmCopy.VALIDATION_MINUTE_RANGE, editor.review(args(12, -1)).validationError)
    }

    @Test
    fun `malformed number is rejected without a stale summary`() {
        val editor = requireNotNull(tool.argumentEditor(args(12, 30)))
        val malformed =
            buildJsonObject {
                put("hour", "tomorrow")
                put("minute", 30)
            }

        val review = editor.review(malformed)

        assertEquals(AlarmCopy.RESULT_INVALID_ARGS, review.validationError)
        assertEquals(AlarmCopy.CARD_SUMMARY_INVALID_ARGS, review.description.summary)
    }

    @Test
    fun `missing required minute is rejected`() {
        val editor = requireNotNull(tool.argumentEditor(args(12, 30)))
        val missingMinute = buildJsonObject { put("hour", 12) }

        assertEquals(AlarmCopy.RESULT_INVALID_ARGS, editor.review(missingMinute).validationError)
    }

    private fun args(
        hour: Int,
        minute: Int,
        message: String? = null,
    ): JsonObject =
        buildJsonObject {
            put("hour", hour)
            put("minute", minute)
            if (message != null) put("message", message)
        }

    private object NoopPort : AlarmIntentPort {
        override fun launch(spec: AlarmIntentSpec): AlarmLaunchResult = AlarmLaunchResult.Started
    }
}
