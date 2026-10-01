package com.example.data.local

import kotlinx.coroutines.flow.Flow

class SanaRepository(private val dao: SanaDao) {

    val allMemories: Flow<List<SanaMemory>> = dao.getAllMemories()
    val allMessages: Flow<List<SanaMessage>> = dao.getAllMessages()

    suspend fun getApprovedMemories(): List<SanaMemory> = dao.getApprovedMemoriesList()

    suspend fun saveMemory(key: String, value: String, category: String = "general"): Long {
        return dao.insertMemory(
            SanaMemory(
                key = key.trim(),
                value = value.trim(),
                category = category,
                userApproved = true
            )
        )
    }

    suspend fun deleteMemory(memory: SanaMemory) {
        dao.deleteMemory(memory)
    }

    suspend fun clearAllMemories() {
        dao.clearAllMemories()
    }

    suspend fun saveMessage(
        sender: String,
        text: String,
        emotion: String = "neutral",
        toolExecuted: String? = null
    ): Long {
        return dao.insertMessage(
            SanaMessage(
                sender = sender,
                text = text,
                emotion = emotion,
                toolExecuted = toolExecuted
            )
        )
    }

    suspend fun getRecentMessages(limit: Int = 10): List<SanaMessage> {
        return dao.getRecentMessages(limit)
    }

    suspend fun clearHistory() {
        dao.clearAllMessages()
    }
}
