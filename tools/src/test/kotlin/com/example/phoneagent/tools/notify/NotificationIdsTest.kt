package com.example.phoneagent.tools.notify

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class NotificationIdsTest {
    @Test
    public fun `first id equals the base`() {
        assertEquals(1_000, NotificationIds().nextId())
    }

    @Test
    public fun `each id is greater than the previous id`() {
        val ids = NotificationIds()

        assertTrue(ids.nextId() < ids.nextId())
    }

    @Test
    public fun `consecutive ids are unique`() {
        val ids = NotificationIds()
        val values = List(100) { ids.nextId() }

        assertEquals(values.size, values.toSet().size)
    }

    @Test
    public fun `overflow returns to the base without a negative id`() {
        val ids = NotificationIds(Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, ids.nextId())
        val afterOverflow = ids.nextId()

        assertEquals(1_000, afterOverflow)
        assertTrue(afterOverflow >= 0)
    }

    @Test
    public fun `concurrent coroutines receive unique ids`() =
        runTest {
            val ids = NotificationIds()
            val values =
                List(8) {
                    async {
                        buildList {
                            repeat(100) {
                                add(ids.nextId())
                                yield()
                            }
                        }
                    }
                }.awaitAll().flatten()

            assertEquals(values.size, values.toSet().size)
        }
}
