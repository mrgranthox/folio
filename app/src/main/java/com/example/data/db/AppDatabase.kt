package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.DuplicateEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UnrecognizedMessageEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        DuplicateEntity::class,
        UnrecognizedMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun duplicateDao(): DuplicateDao
    abstract fun unrecognizedMessageDao(): UnrecognizedMessageDao
}
