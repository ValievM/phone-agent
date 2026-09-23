package com.example.phoneagent.logging

import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.error

/**
 * Logs an uncaught exception before handing it to whatever handler was previously installed
 * (the OS default, which shows the crash dialog and lets debuggers/crash tools see it too).
 * Pure composition, no Android types, so it's unit-testable without a device.
 */
internal fun crashLoggingHandler(
    logger: AppLogger,
    previous: Thread.UncaughtExceptionHandler?,
): Thread.UncaughtExceptionHandler =
    Thread.UncaughtExceptionHandler { thread, throwable ->
        logger.error("Crash", "Uncaught exception", fields = mapOf("thread" to thread.name), throwable = throwable)
        previous?.uncaughtException(thread, throwable)
    }
