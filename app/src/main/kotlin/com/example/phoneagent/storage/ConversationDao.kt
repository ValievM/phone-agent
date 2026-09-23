package com.example.phoneagent.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface ConversationDao {
    @Query("SELECT * FROM conversation_history ORDER BY position ASC")
    suspend fun loadAll(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ConversationEntity>)

    @Query("DELETE FROM conversation_history")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ConversationEntity>) {
        deleteAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}
