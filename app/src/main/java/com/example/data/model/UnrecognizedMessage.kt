package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "unrecognized_messages")
data class UnrecognizedMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val rawBody: String,
    val timestamp: Long = System.currentTimeMillis(),
    val suspectedAmount: String? = null,
    val suspectedMerchant: String? = null,
    val isResolved: Boolean = false
)
