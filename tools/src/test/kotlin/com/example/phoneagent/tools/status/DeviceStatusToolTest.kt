package com.example.phoneagent.tools.status

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeClock
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import dagger.multibindings.IntoSet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class DeviceStatusToolTest {
    @Test
    public fun `reports all device values and exact card summary`() =
        runTest {
            val tool = DeviceStatusTool(FakeDeviceStatusPort(DeviceStatus(76, true, true, 18_637, 64_000)))

            val result = tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success

            assertEquals("Заряд 76%, заряжается. Экономный режим включён. Свободно 18,2 ГБ из 62,5 ГБ.", result.text)
            assertEquals("Состояние телефона", tool.describe(buildJsonObject {}).title)
            assertEquals("Заряд 76%, свободно 18,2 ГБ", tool.describe(buildJsonObject {}).summary)
        }

    @Test
    public fun `omits unavailable battery percentage without inventing a value`() =
        runTest {
            val tool = DeviceStatusTool(FakeDeviceStatusPort(DeviceStatus(null, false, false, 512, 1_024)))

            val result = tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success

            assertEquals("Не заряжается. Экономный режим выключен. Свободно 512 МБ из 1,0 ГБ.", result.text)
            assertFalse(result.text.contains("Заряд"))
        }

    @Test
    public fun `reports charging and not charging and omits unknown charging state`() =
        runTest {
            val port = FakeDeviceStatusPort(DeviceStatus(76, true, null, 0, 0))
            val tool = DeviceStatusTool(port)

            val charging = (tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success).text
            port.status = DeviceStatus(76, false, null, 0, 0)
            val notCharging = (tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success).text
            port.status = DeviceStatus(76, null, null, 0, 0)
            val unknown = (tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success).text

            assertTrue(charging.contains("Заряд 76%, заряжается."))
            assertTrue(notCharging.contains("Заряд 76%, не заряжается."))
            assertEquals("Заряд 76%. Свободно 0 МБ из 0 МБ.", unknown)
        }

    @Test
    public fun `reports power saver as on off and unknown`() =
        runTest {
            val port = FakeDeviceStatusPort(DeviceStatus(null, null, true, 0, 0))
            val tool = DeviceStatusTool(port)

            val enabled = (tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success).text
            port.status = DeviceStatus(null, null, false, 0, 0)
            val disabled = (tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success).text
            port.status = DeviceStatus(null, null, null, 0, 0)
            val unknown = (tool.execute(buildJsonObject {}, toolContext()) as ToolResult.Success).text

            assertTrue(enabled.contains("Экономный режим включён."))
            assertTrue(disabled.contains("Экономный режим выключен."))
            assertFalse(unknown.contains("Экономный режим"))
        }

    @Test
    public fun `formats free storage in megabytes and gigabytes without rounding down to zero`() =
        runTest {
            val port = FakeDeviceStatusPort(DeviceStatus(null, null, null, 0, 0))
            val tool = DeviceStatusTool(port)

            suspend fun summary(freeMb: Long): String {
                port.status = DeviceStatus(null, null, null, freeMb, 10_485_760)
                return tool.describe(buildJsonObject {}).summary
            }

            assertEquals("свободно 0 МБ", summary(0))
            assertEquals("свободно 512 МБ", summary(512))
            assertEquals("свободно 1,5 ГБ", summary(1_536))
            assertEquals("свободно 10 240,0 ГБ", summary(10_485_760))
        }

    @Test
    public fun `zero total storage does not divide by zero`() =
        runTest {
            val tool = DeviceStatusTool(FakeDeviceStatusPort(DeviceStatus(10, false, false, 0, 0)))

            val result = tool.execute(buildJsonObject {}, toolContext())
            val description = tool.describe(buildJsonObject {})

            assertTrue(result is ToolResult.Success)
            assertEquals("Заряд 10%, свободно 0 МБ", description.summary)
        }

    @Test
    public fun `is safe and does not log device values`() =
        runTest {
            val logger = FakeAppLogger()
            val tool = DeviceStatusTool(FakeDeviceStatusPort(DeviceStatus(76, true, false, 18_637, 64_000)))

            assertEquals("get_device_status", tool.spec.name)
            assertEquals(Risk.SAFE, tool.spec.risk)
            tool.execute(buildJsonObject {}, ToolContext(FakeClock(), logger))

            assertTrue(logger.events.isEmpty())
        }

    @Test
    public fun `ignores arbitrary arguments`() =
        runTest {
            val tool = DeviceStatusTool(FakeDeviceStatusPort(DeviceStatus(76, null, null, 512, 1_024)))

            val result = tool.execute(buildJsonObject { put("untrusted", "value") }, toolContext())

            assertTrue(result is ToolResult.Success)
            assertEquals("Заряд 76%. Свободно 512 МБ из 1,0 ГБ.", (result as ToolResult.Success).text)
        }

    @Test
    public fun `reports a read failure without throwing`() =
        runTest {
            val tool =
                DeviceStatusTool(
                    object : DeviceStatusPort {
                        override fun read(): DeviceStatus = error("unavailable")
                    },
                )

            assertEquals(ToolResult.Failed(DeviceStatusCopy.READ_ERROR), tool.execute(buildJsonObject {}, toolContext()))
            assertEquals(DeviceStatusCopy.READ_ERROR, tool.describe(buildJsonObject {}).summary)
        }

    @Test
    public fun `tool is registered into the tool set`() {
        val binding =
            DeviceStatusBindings::class.java.declaredMethods.single { it.name == "bindDeviceStatusTool" }

        assertNotNull(binding.getAnnotation(IntoSet::class.java))
    }

    private fun toolContext(): ToolContext = ToolContext(FakeClock(), FakeAppLogger())
}

private class FakeDeviceStatusPort(
    var status: DeviceStatus,
) : DeviceStatusPort {
    override fun read(): DeviceStatus = status
}
