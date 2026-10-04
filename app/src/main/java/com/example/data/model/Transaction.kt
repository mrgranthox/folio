package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TransactionDirection(val value: String) {
    DEBIT("DEBIT"),
    CREDIT("CREDIT");

    companion object {
        fun fromString(value: String): TransactionDirection {
            return if (value.equals("CREDIT", ignoreCase = true) ||
                value.equals("INCOME", ignoreCase = true) ||
                value.equals("RECEIVED", ignoreCase = true)
            ) {
                CREDIT
            } else {
                DEBIT
            }
        }
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val externalRef: String? = null,
    val accountId: String,
    val categoryId: String,
    val amount: Double, // Negative for debit, positive for credit
    val currency: String = "GHS",
    val direction: String = "DEBIT",
    val type: String = "Expense", // "Expense", "Income", "Transfer"
    val timestamp: Long = System.currentTimeMillis(),
    val counterparty: String,
    val sourceMethod: String = "manual", // "manual", "sms", "ocr"
    val notes: String? = null,
    val receiptImagePath: String? = null,
    val isVerified: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val accountRail: String? = null
) {
    val isIncome: Boolean
        get() = amount > 0 || direction.equals("CREDIT", ignoreCase = true)

    val isExpense: Boolean
        get() = !isIncome

    val transactionDirection: TransactionDirection
        get() = TransactionDirection.fromString(direction)
}
