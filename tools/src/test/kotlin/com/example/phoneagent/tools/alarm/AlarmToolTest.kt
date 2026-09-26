package com.example.phoneagent.tools.alarm

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.tools.ToolContent
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AlarmToolTest {
    private val context = ToolContext(FakeClock(), FakeAppLogger())

    @Test
    fun `consecutive alarms reach the clock app at least the spacing apart`() =
        runTest {
            val port = RecordingPort(this)
            val tool = AlarmTool(port, spacing = 1_000.milliseconds, timeSource = TestTimeSource())

            repeat(3) { tool.execute(alarmArgs(minute = it * 15), context) }

            // Fired back to back, the clock app takes only the first request and drops the rest.
            assertEquals(listOf(0L, 1_000L, 2_000L), port.launchTimesMillis)
        }

    @Test
    fun `an alarm after a long pause is launched immediately`() =
        runTest {
            val port = RecordingPort(this)
            val time = TestTimeSource()
            val tool = AlarmTool(port, spacing = 1_000.milliseconds, timeSource = time)

            tool.execute(alarmArgs(minute = 0), context)
            time += 5.seconds
            tool.execute(alarmArgs(minute = 15), context)

            assertEquals(listOf(0L, 0L), port.launchTimesMillis)
        }

    @Test
    fun `a single alarm is launched without any delay and reports success`() =
        runTest {
            val port = RecordingPort(this)
            val tool = AlarmTool(port, spacing = 1_000.milliseconds, timeSource = TestTimeSource())

            val result = tool.execute(alarmArgs(minute = 30), context)

            assertEquals(listOf(0L), port.launchTimesMillis)
            assertTrue(result is ToolResult.Success)
        }

    @Test
    fun `a started alarm returns a handoff with the existing model output`() =
        runTest {
            val tool = AlarmTool(RecordingPort(this), spacing = 1_000.milliseconds, timeSource = TestTimeSource())

            val result = tool.execute(alarmArgs(minute = 30), context) as ToolResult.Success

            assertEquals(AlarmCopy.RESULT_SUCCESS, result.text)
            assertEquals(ToolContent.HandedOff(AlarmCopy.TARGET_APP), result.content)
        }

    @Test
    fun `old success output restores its handoff but other output does not`() =
        runTest {
            val tool = AlarmTool(RecordingPort(this), spacing = 1_000.milliseconds, timeSource = TestTimeSource())

            assertEquals(ToolContent.HandedOff(AlarmCopy.TARGET_APP), tool.restoreContent(AlarmCopy.RESULT_SUCCESS))
            assertEquals(null, tool.restoreContent("not an alarm result"))
        }

    @Test
    fun `a refused launch is reported as a failure`() =
        runTest {
            val tool = AlarmTool(RecordingPort(this, AlarmLaunchResult.Failed), spacing = 1_000.milliseconds, timeSource = TestTimeSource())

            assertEquals(ToolResult.Failed(AlarmCopy.RESULT_FAILED), tool.execute(alarmArgs(minute = 30), context))
        }

    @Test
    fun `a missing clock app is reported as a failure`() =
        runTest {
            val port = RecordingPort(this, AlarmLaunchResult.NoClockApp)
            val tool = AlarmTool(port, spacing = 1_000.milliseconds, timeSource = TestTimeSource())

            val result = tool.execute(alarmArgs(minute = 30), context)

            assertEquals(ToolResult.Failed(AlarmCopy.RESULT_NO_CLOCK_APP), result)
        }

    private fun alarmArgs(minute: Int): JsonObject =
        buildJsonObject {
            put("hour", 12)
            put("minute", minute)
        }

    private class RecordingPort(
        private val scope: TestScope,
        private val result: AlarmLaunchResult = AlarmLaunchResult.Started,
    ) : AlarmIntentPort {
        val launchTimesMillis = mutableListOf<Long>()

        override fun launch(spec: AlarmIntentSpec): AlarmLaunchResult {
            launchTimesMillis += scope.testScheduler.currentTime
            return result
        }
    }
}
