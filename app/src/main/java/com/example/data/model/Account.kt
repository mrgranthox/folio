package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AccountType(val displayName: String) {
    MOMO("Mobile Money"),
    BANK("Bank Account"),
    CASH("Cash"),
    CARD("Card");

    companion object {
        fun fromString(value: String): AccountType {
            return when (value.lowercase().trim()) {
                "momo", "mobile money", "mtn momo", "telecel cash", "at money" -> MOMO
                "bank", "bank account", "bank transfer", "checking", "savings" -> BANK
                "cash" -> CASH
                "card", "debit card", "credit card", "visa", "mastercard" -> CARD
                else -> CARD
            }
        }
    }
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accountType: String = AccountType.MOMO.name,
    val currentBalance: Double = 0.0,
    val currency: String = "GHS",
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    val type: AccountType
        get() = AccountType.fromString(accountType)
}
