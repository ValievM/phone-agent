package com.example.phoneagent.logging

import com.example.phoneagent.domain.platform.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidLogcatLoggerTest {
    private val secretFileName = "private_contract.pdf"
    private val secretUserText = "мой секретный пользовательский текст"

    private class RecordedLogcat(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    private val logcat = mutableListOf<RecordedLogcat>()
    private val logcatSink: (LogLevel, String, String, Throwable?) -> Unit = { level, tag, message, throwable ->
        logcat += RecordedLogcat(level, tag, message, throwable)
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private fun logger(logFile: File) = AndroidLogcatLogger(file = logFile, logcat = logcatSink)

    @Test
    fun `a secret inside a throwable message or cause reaches neither logcat nor the file`() {
        val logFile = tmp.newFile("app.log")
        val failure = RuntimeException(secretFileName, IllegalStateException(secretUserText))

        logger(logFile).log(LogLevel.ERROR, "Model", "turn failed", throwable = failure)

        val logcatText =
            logcat.joinToString("\n") { recorded ->
                recorded.message + " " +
                    (recorded.throwable?.let { "${it.message} ${it.cause?.message} ${it.stackTraceToString()}" } ?: "")
            }
        val fileText = logFile.readText()
        assertFalse(logcatText.contains(secretFileName))
        assertFalse(logcatText.contains(secretUserText))
        assertFalse(fileText.contains(secretFileName))
        assertFalse(fileText.contains(secretUserText))
        // Diagnosis survives: the exception type and the frames are still there.
        assertEquals("RuntimeException", logcat.single().throwable?.let { it::class.simpleName })
        assertTrue(
            logcat
                .single()
                .throwable
                ?.stackTrace
                ?.size!! > 0,
        )
        assertTrue(fileText.contains("RuntimeException"))
    }

    @Test
    fun `a failed file write never breaks logging and logcat still gets the line`() {
        // A regular file where a directory is expected: any write under it fails.
        val blocker = tmp.newFile("blocker")
        val logFile = File(blocker, "app.log")

        logger(logFile).log(LogLevel.WARN, "Model", "turn failed")

        assertEquals("turn failed", logcat.single().message)
    }
}
