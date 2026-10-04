package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "briefcase", // "utensils", "car", "shopping-bag", "zap", "film", "heart-pulse", "briefcase", "coins", "wallet"
    val colorHex: String = "#3B82F6",
    val budgetLimit: Double? = null,
    val isDefault: Boolean = false,
    val isDeleted: Boolean = false
)
