package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SanaDao {

    // Memory operations
    @Query("SELECT * FROM sana_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<SanaMemory>>

    @Query("SELECT * FROM sana_memories WHERE userApproved = 1")
    suspend fun getApprovedMemoriesList(): List<SanaMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: SanaMemory): Long

    @Delete
    suspend fun deleteMemory(memory: SanaMemory)

    @Query("DELETE FROM sana_memories")
    suspend fun clearAllMemories()

    // Message history operations
    @Query("SELECT * FROM sana_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<SanaMessage>>

    @Query("SELECT * FROM sana_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<SanaMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SanaMessage): Long

    @Query("DELETE FROM sana_messages")
    suspend fun clearAllMessages()
}
