package com.example.phoneagent.tools.alarm

import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.Tool
import com.example.phoneagent.domain.tools.ToolContext
import com.example.phoneagent.domain.tools.ToolResult
import com.example.phoneagent.domain.tools.ToolSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

internal class AlarmTool
    @Inject
    constructor(
        private val intentLauncher: AlarmIntentLauncher,
    ) : Tool {
        override val spec: ToolSpec =
            ToolSpec(
                name = "set_alarm",
                description = "Set an alarm on the device.",
                inputSchema = alarmInputSchema,
                risk = Risk.CONFIRM,
            )

        override fun describe(args: JsonObject): CallDescription {
            val alarmArgs = decodeArgs(args)
                ?: return CallDescription(title = "Поставить будильник", summary = "С неверными параметрами")
            val label = alarmArgs.message?.let { ". Название: $it" }.orEmpty()
            return CallDescription(
                title = "Поставить будильник",
                summary = "На ${alarmArgs.hour.toString().padStart(2, '0')}:${alarmArgs.minute.toString().padStart(2, '0')}$label",
            )
        }

        override suspend fun execute(
            args: JsonObject,
            context: ToolContext,
        ): ToolResult =
            decodeArgs(args)?.let(::executeAlarm) ?: ToolResult.Failed("Неверные параметры будильника")

        private fun executeAlarm(alarmArgs: AlarmArgs): ToolResult =
            when (val validation = validateAlarmArgs(alarmArgs)) {
                is AlarmValidationResult.Invalid -> ToolResult.Failed(validation.reason)
                AlarmValidationResult.Valid ->
                    when (intentLauncher.launch(AlarmIntentFactory.create(alarmArgs))) {
                        AlarmLaunchResult.Started -> ToolResult.Success("Запрос на будильник отправлен")
                        AlarmLaunchResult.NoClockApp -> ToolResult.Failed("Не найдено приложение «Часы»")
                        AlarmLaunchResult.Failed -> ToolResult.Failed("Не удалось отправить запрос на будильник")
                    }
            }
    }

private val alarmInputSchema: JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("object"))
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", JsonPrimitive("integer"))
                put("minimum", JsonPrimitive(0))
                put("maximum", JsonPrimitive(23))
            }
            putJsonObject("minute") {
                put("type", JsonPrimitive("integer"))
                put("minimum", JsonPrimitive(0))
                put("maximum", JsonPrimitive(59))
            }
            putJsonObject("message") {
                put("type", JsonPrimitive("string"))
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("hour")); add(JsonPrimitive("minute")) })
        put("additionalProperties", JsonPrimitive(false))
    }

private fun decodeArgs(args: JsonObject): AlarmArgs? =
    try {
        Json.decodeFromJsonElement<AlarmArgs>(args)
    } catch (_: SerializationException) {
        null
    }
