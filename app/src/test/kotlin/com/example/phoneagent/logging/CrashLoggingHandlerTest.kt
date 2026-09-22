package com.example.phoneagent.logging

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.platform.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashLoggingHandlerTest {
    // Redaction itself is RedactingAppLogger's job (see RedactingAppLoggerTest); this handler only
    // needs to log through whatever AppLogger it is given, which DI always wires as the redacting
    // one, and to still let the app's own default crash reporting run afterwards.
    @Test
    fun `logs the crash before delegating to the previous handler`() {
        val logger = FakeAppLogger()
        val calls = mutableListOf<Pair<Thread, Throwable>>()
        val previous = Thread.UncaughtExceptionHandler { thread, throwable -> calls += thread to throwable }
        val handler = crashLoggingHandler(logger, previous)
        val failure = IllegalStateException("boom")

        handler.uncaughtException(Thread.currentThread(), failure)

        val event = logger.events.single()
        assertEquals(LogLevel.ERROR, event.level)
        assertEquals(failure, event.throwable)
        assertEquals(listOf(Thread.currentThread() to failure), calls)
    }

    @Test
    fun `still works when there is no previous handler`() {
        val logger = FakeAppLogger()
        val handler = crashLoggingHandler(logger, previous = null)

        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertEquals(1, logger.events.size)
    }
}
