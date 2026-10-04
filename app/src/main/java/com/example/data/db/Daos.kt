package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.DuplicateEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UnrecognizedMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE externalRef = :externalRef AND isDeleted = 0 LIMIT 1")
    suspend fun getTransactionByExternalRef(externalRef: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getAllAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET currentBalance = :balance WHERE id = :id")
    suspend fun updateBalance(id: String, balance: Double)

    @Query("UPDATE accounts SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM accounts")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name ASC")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}

@Dao
interface DuplicateDao {
    @Query("SELECT * FROM duplicates ORDER BY createdAt DESC")
    fun getAllDuplicates(): Flow<List<DuplicateEntity>>

    @Query("SELECT * FROM duplicates ORDER BY createdAt DESC")
    suspend fun getAllDuplicatesList(): List<DuplicateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuplicate(duplicate: DuplicateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuplicates(duplicates: List<DuplicateEntity>)

    @Query("DELETE FROM duplicates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM duplicates")
    suspend fun clearAll()
}

@Dao
interface UnrecognizedMessageDao {
    @Query("SELECT * FROM unrecognized_messages WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getActiveMessages(): Flow<List<UnrecognizedMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: UnrecognizedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<UnrecognizedMessageEntity>)

    @Query("UPDATE unrecognized_messages SET isResolved = 1 WHERE id = :id")
    suspend fun markResolved(id: String)

    @Query("DELETE FROM unrecognized_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM unrecognized_messages")
    suspend fun clearAll()
}
