package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "duplicates")
data class DuplicateEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val existingTransactionId: String,
    // JSON or serializable fields for imported candidate
    val importedExternalRef: String? = null,
    val importedAccountId: String,
    val importedCategoryId: String,
    val importedAmount: Double,
    val importedCurrency: String = "GHS",
    val importedDirection: String = "DEBIT",
    val importedType: String = "Expense",
    val importedTimestamp: Long,
    val importedCounterparty: String,
    val importedSourceMethod: String = "sms",
    val importedNotes: String? = null,
    val importedReceiptImagePath: String? = null,
    val importedAccountRail: String? = null,
    val matchScore: Int,
    val matchFactors: String, // Comma or semicolon separated
    val createdAt: Long = System.currentTimeMillis()
)

data class DuplicateCandidateItem(
    val id: String,
    val existingTransaction: TransactionEntity,
    val importedTransaction: TransactionEntity,
    val matchScore: Int,
    val matchFactors: List<String>
)
