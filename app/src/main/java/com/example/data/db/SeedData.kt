package com.example.data.db

import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.DuplicateEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UnrecognizedMessageEntity

object SeedData {

    fun getDefaultAccounts(): List<AccountEntity> = emptyList()

    fun getDefaultCategories(): List<CategoryEntity> = emptyList()

    fun getDefaultTransactions(): List<TransactionEntity> = emptyList()

    fun getDefaultDuplicates(): List<DuplicateEntity> = emptyList()

    fun getDefaultUnrecognizedMessages(): List<UnrecognizedMessageEntity> = emptyList()
}
