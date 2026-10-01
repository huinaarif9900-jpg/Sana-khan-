package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sana_memories")
data class SanaMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis(),
    val userApproved: Boolean = true
)

@Entity(tableName = "sana_messages")
data class SanaMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user" or "sana"
    val text: String,
    val emotion: String = "neutral",
    val toolExecuted: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
