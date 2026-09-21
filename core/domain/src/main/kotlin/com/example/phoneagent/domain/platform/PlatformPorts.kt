package com.example.phoneagent.domain.platform

import kotlinx.coroutines.CoroutineDispatcher

public fun interface Clock {
    public fun nowEpochMillis(): Long
}

public interface DispatcherProvider {
    public val main: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val default: CoroutineDispatcher
}

public interface AppLogger {
    public fun debug(message: String)

    public fun error(
        message: String,
        throwable: Throwable? = null,
    )
}
