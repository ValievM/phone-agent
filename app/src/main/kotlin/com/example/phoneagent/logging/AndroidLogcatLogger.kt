package com.example.phoneagent.logging

import android.content.Context
import android.util.Log
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.LogLevel
import com.example.phoneagent.domain.platform.RingLog
import java.io.File

/**
 * The one file in the app allowed to touch [android.util.Log] directly (see
 * ArchitectureTest.production code uses AppLogger instead of Android Log). Always bind this
 * behind [com.example.phoneagent.domain.platform.RedactingAppLogger], never as [AppLogger] itself.
 *
 * Besides Logcat, it keeps a small rolling copy of recent log lines in a file under
 * noBackupFilesDir, so a crash or a support request has something to look at even if the device's
 * own Logcat buffer already scrolled past it. The trimming itself is [RingLog], tested without
 * Android; this class is a thin, untested-by-unit-test wrapper around it and disk I/O.
 */
public class AndroidLogcatLogger(
    context: Context,
    private val maxFileBytes: Int = MAX_FILE_BYTES,
) : AppLogger {
    private val file: File = File(context.applicationContext.noBackupFilesDir, LOG_FILE_NAME)
    private val lock = Any()

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        appendToFile(formatLine(level, tag, message, fields, throwable))
    }

    private fun appendToFile(line: String) {
        synchronized(lock) {
            val existing = runCatching { file.readText() }.getOrDefault("")
            file.writeText(RingLog.append(existing, line, maxFileBytes))
        }
    }

    private fun formatLine(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ): String {
        val fieldsText = if (fields.isEmpty()) "" else " " + fields.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        val throwableText = throwable?.let { " | ${it::class.qualifiedName}: ${it.message}" }.orEmpty()
        return "${System.currentTimeMillis()} $level/$tag: $message$fieldsText$throwableText"
    }

    private companion object {
        private const val LOG_FILE_NAME: String = "app.log"
        private const val MAX_FILE_BYTES: Int = 64 * 1024
    }
}
