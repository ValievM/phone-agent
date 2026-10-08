package com.example.phoneagent.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseRoomSmokeTest {
    @Test
    fun `release unit variant stores and reads a conversation`() =
        runTest {
            val database =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        AppDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()
            try {
                val message = ConversationEntity(position = 0, id = "user-1", type = "user", text = "Hello")
                database.conversationDao().insertAll(listOf(message))

                assertEquals(listOf(message), database.conversationDao().loadAll())
            } finally {
                database.close()
            }
        }
}
