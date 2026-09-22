package com.example.phoneagent.domain.platform

import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID

public fun interface Clock {
    public fun nowEpochMillis(): Long
}

public interface DispatcherProvider {
    public val main: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val default: CoroutineDispatcher
}

public enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

public interface AppLogger {
    public fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    )
}

public fun AppLogger.debug(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
): Unit = log(LogLevel.DEBUG, tag, message, fields)

public fun AppLogger.info(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
): Unit = log(LogLevel.INFO, tag, message, fields)

public fun AppLogger.warn(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
    throwable: Throwable? = null,
): Unit = log(LogLevel.WARN, tag, message, fields, throwable)

public fun AppLogger.error(
    tag: String,
    message: String,
    fields: Map<String, String> = emptyMap(),
    throwable: Throwable? = null,
): Unit = log(LogLevel.ERROR, tag, message, fields, throwable)

/** One id per logical operation (e.g. a sign-in attempt), so its scattered log lines can be tied together. */
public fun newCorrelationId(): String = UUID.randomUUID().toString()
