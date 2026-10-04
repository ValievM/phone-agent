package com.example.phoneagent.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class CurrentTimeNoteTest {
    @Test
    fun `renders local date time zone offset and ISO seconds`() {
        val note = CurrentTimeNote.render(Instant.parse("2026-10-04T00:57:12Z").toEpochMilli(), ZoneId.of("Europe/Moscow"))

        assertEquals(
            "Сейчас: воскресенье, 4 октября 2026, 03:57 (часовой пояс Europe/Moscow, UTC+03:00). ISO: 2026-10-04T03:57:12+03:00.",
            note,
        )
    }

    @Test
    fun `same instant is rendered in each supplied zone`() {
        val instant = Instant.parse("2026-10-04T00:57:12Z").toEpochMilli()

        assertEquals(
            "Сейчас: суббота, 3 октября 2026, 20:57 (часовой пояс America/New_York, UTC-04:00). ISO: 2026-10-03T20:57:12-04:00.",
            CurrentTimeNote.render(instant, ZoneId.of("America/New_York")),
        )
        assertEquals(
            "Сейчас: воскресенье, 4 октября 2026, 09:57 (часовой пояс Asia/Tokyo, UTC+09:00). ISO: 2026-10-04T09:57:12+09:00.",
            CurrentTimeNote.render(instant, ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `local date and weekday cross midnight in the supplied zone`() {
        val instant = Instant.parse("2026-10-04T00:30:00Z").toEpochMilli()

        assertEquals(
            "Сейчас: суббота, 3 октября 2026, 20:30 (часовой пояс America/New_York, UTC-04:00). ISO: 2026-10-03T20:30:00-04:00.",
            CurrentTimeNote.render(instant, ZoneId.of("America/New_York")),
        )
        assertEquals(
            "Сейчас: воскресенье, 4 октября 2026, 09:30 (часовой пояс Asia/Tokyo, UTC+09:00). ISO: 2026-10-04T09:30:00+09:00.",
            CurrentTimeNote.render(instant, ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `daylight saving offset follows the date`() {
        val zone = ZoneId.of("America/New_York")

        assertEquals(
            "Сейчас: суббота, 7 марта 2026, 12:00 (часовой пояс America/New_York, UTC-05:00). ISO: 2026-03-07T12:00:00-05:00.",
            CurrentTimeNote.render(Instant.parse("2026-03-07T17:00:00Z").toEpochMilli(), zone),
        )
        assertEquals(
            "Сейчас: воскресенье, 8 марта 2026, 12:00 (часовой пояс America/New_York, UTC-04:00). ISO: 2026-03-08T12:00:00-04:00.",
            CurrentTimeNote.render(Instant.parse("2026-03-08T16:00:00Z").toEpochMilli(), zone),
        )
    }

    @Test
    fun `all weekday and month names are Russian`() {
        val weekdays = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
        for (day in 1..7) {
            val instant = Instant.parse("2026-10-05T00:00:00Z").plusSeconds((day - 1) * 86_400L).toEpochMilli()
            assertEquals(true, CurrentTimeNote.render(instant, ZoneId.of("UTC")).contains(weekdays[day - 1]))
        }
        val months =
            listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
        for (month in 1..12) {
            val instant =
                java.time.LocalDate
                    .of(2026, month, 1)
                    .atTime(12, 0)
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli()
            val note = CurrentTimeNote.render(instant, ZoneId.of("UTC"))
            assertEquals(true, note.contains(months[month - 1]))
        }
    }

    @Test
    fun `rendering does not depend on the default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals(
                "Сейчас: воскресенье, 4 октября 2026, 03:57 (часовой пояс Europe/Moscow, UTC+03:00). ISO: 2026-10-04T03:57:12+03:00.",
                CurrentTimeNote.render(Instant.parse("2026-10-04T00:57:12Z").toEpochMilli(), ZoneId.of("Europe/Moscow")),
            )
        } finally {
            Locale.setDefault(original)
        }
    }
}
