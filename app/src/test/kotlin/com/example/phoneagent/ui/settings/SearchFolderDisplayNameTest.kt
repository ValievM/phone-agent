package com.example.phoneagent.ui.settings

import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFolderDisplayNameTest {
    @Test
    fun `folder display name failures log only the exception type`() {
        val logger = RecordingLogger()

        logFolderDisplayNameFailure(logger, IllegalStateException("private folder URI"))

        assertEquals(LogLevel.WARN, logger.level)
        assertEquals("read search folder display name failed", logger.message)
        assertEquals(mapOf("exceptionType" to "IllegalStateException"), logger.fields)
        assertNull(logger.throwable)
    }

    private class RecordingLogger : AppLogger {
        var level: LogLevel? = null
        var message: String? = null
        var fields: Map<String, String> = emptyMap()
        var throwable: Throwable? = null

        override fun log(
            level: LogLevel,
            tag: String,
            message: String,
            fields: Map<String, String>,
            throwable: Throwable?,
        ) {
            this.level = level
            this.message = message
            this.fields = fields
            this.throwable = throwable
        }
    }
}
