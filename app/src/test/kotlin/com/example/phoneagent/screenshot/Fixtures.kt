package com.example.phoneagent.screenshot

import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.ui.designsystem.components.FileHit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val ALARM_HOUR = 7
private const val ALARM_MINUTE = 30
private const val FILE_SIZE_BYTES = 482_133L

internal val alarmDescription =
    CallDescription(
        title = "Будильник",
        summary = "07:30 · «Работа»",
        details = listOf("Сработает один раз, сегодня"),
    )

internal val alarmArguments: JsonObject =
    buildJsonObject {
        put("hour", ALARM_HOUR)
        put("minute", ALARM_MINUTE)
        put("label", "Работа")
    }

internal val alarmApproval =
    ApprovalRequest(description = alarmDescription, risk = Risk.CONFIRM, arguments = alarmArguments)

internal val fileHits =
    listOf(
        FileHit("Договор аренды.pdf", "content://media/external/file/1", FILE_SIZE_BYTES, "application/pdf"),
        FileHit("scan_2026-09-01.jpg", "content://media/external/file/2", null, "image/jpeg"),
    )

internal const val LONG_CYRILLIC =
    "Чрезвычайнопродолжительноесловоневлезающеевстрокуприобычныхнастройкахшрифта и обычный текст после него"
