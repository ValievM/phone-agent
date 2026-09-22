package com.example.phoneagent.domain.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingAppLoggerTest {
    @Test
    fun `redacts the message before it reaches the delegate`() {
        val delegate = RecordingLogger()
        val logger = RedactingAppLogger(delegate)

        logger.log(LogLevel.ERROR, "Auth", "failed with Bearer super-secret-token")

        assertEquals("failed with Bearer <redacted>", delegate.events.single().message)
    }

    @Test
    fun `redacts field values but keeps field names`() {
        val delegate = RecordingLogger()
        val logger = RedactingAppLogger(delegate)

        logger.log(LogLevel.INFO, "Auth", "sign in", fields = mapOf("access_token" to "secret-value", "status" to "200"))

        val fields = delegate.events.single().fields
        assertEquals("200", fields["status"])
        assertFalse(fields.getValue("access_token").contains("secret-value"))
    }

    @Test
    fun `replaces the throwable message but keeps its stack trace for diagnosis`() {
        val delegate = RecordingLogger()
        val logger = RedactingAppLogger(delegate)
        val original = IllegalStateException("token=super-secret-value")

        logger.log(LogLevel.ERROR, "Auth", "exchange failed", throwable = original)

        val logged = delegate.events.single().throwable
        assertFalse(logged?.message.orEmpty().contains("super-secret-value"))
        assertEquals(original.stackTrace.toList(), logged?.stackTrace?.toList())
    }

    @Test
    fun `passes through level and tag unchanged`() {
        val delegate = RecordingLogger()
        val logger = RedactingAppLogger(delegate)

        logger.log(LogLevel.WARN, "Network", "retrying")

        val event = delegate.events.single()
        assertEquals(LogLevel.WARN, event.level)
        assertEquals("Network", event.tag)
    }

    private data class Recorded(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val fields: Map<String, String>,
        val throwable: Throwable?,
    )

    private class RecordingLogger : AppLogger {
        val events: MutableList<Recorded> = mutableListOf()

        override fun log(
            level: LogLevel,
            tag: String,
            message: String,
            fields: Map<String, String>,
            throwable: Throwable?,
        ) {
            events += Recorded(level, tag, message, fields, throwable)
        }
    }
}
