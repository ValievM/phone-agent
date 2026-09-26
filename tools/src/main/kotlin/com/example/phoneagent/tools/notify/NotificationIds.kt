package com.example.phoneagent.tools.notify

import java.util.concurrent.atomic.AtomicInteger

internal class NotificationIds(
    firstId: Int = BASE_ID,
) {
    private val counter = AtomicInteger(firstId)

    fun nextId(): Int {
        while (true) {
            val currentId = counter.get()
            val nextId = if (currentId == Int.MAX_VALUE) BASE_ID else currentId + 1
            if (counter.compareAndSet(currentId, nextId)) return currentId
        }
    }

    private companion object {
        const val BASE_ID = 1_000
    }
}
