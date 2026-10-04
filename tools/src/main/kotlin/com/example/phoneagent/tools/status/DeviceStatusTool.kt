package com.example.phoneagent.tools.status

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

internal class DeviceStatusTool
    @Inject
    constructor(
        private val deviceStatusPort: DeviceStatusPort,
    ) : Tool {
        override val spec: ToolSpec =
            ToolSpec(
                name = "get_device_status",
                description = DeviceStatusCopy.MODEL_DESCRIPTION,
                inputSchema = deviceStatusInputSchema,
                risk = Risk.SAFE,
            )

        override fun describe(args: JsonObject): CallDescription =
            try {
                val status = deviceStatusPort.read()
                CallDescription(DeviceStatusCopy.CARD_TITLE, DeviceStatusCopy.cardSummary(status))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CallDescription(DeviceStatusCopy.CARD_TITLE, DeviceStatusCopy.READ_ERROR)
            }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult =
            try {
                ToolResult.Success(DeviceStatusCopy.result(deviceStatusPort.read()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ToolResult.Failed(DeviceStatusCopy.READ_ERROR)
            }
    }

private val deviceStatusInputSchema: JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", JsonPrimitive(true))
    }
