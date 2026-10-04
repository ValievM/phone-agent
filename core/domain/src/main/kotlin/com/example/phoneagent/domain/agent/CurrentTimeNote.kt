package com.example.phoneagent.domain.agent

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

public object CurrentTimeNote {
    private val humanDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

    public fun render(
        epochMillis: Long,
        zone: ZoneId,
    ): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val offset = dateTime.offset.id.let { if (it == "Z") "+00:00" else it }
        return "Сейчас: ${humanDateFormatter.format(
            dateTime,
        )} (часовой пояс ${zone.id}, UTC$offset). ISO: ${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime)}."
    }
}
