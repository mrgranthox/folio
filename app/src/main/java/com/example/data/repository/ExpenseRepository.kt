package com.example.data.repository

import android.content.Context
import android.util.Base64
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.db.SeedData
import com.example.data.engine.ParsedSmsResult
import com.example.data.engine.ReceiptDraft
import com.example.data.engine.ReceiptParserEngine
import com.example.data.engine.ReconciliationEngine
import com.example.data.engine.ReconciliationOutcome
import com.example.data.engine.ReconciliationResult
import com.example.data.engine.SmsParserEngine
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.DuplicateCandidateItem
import com.example.data.model.DuplicateEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UnrecognizedMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExpenseRepository(context: Context) {

    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "folio_expenses.db"
    ).fallbackToDestructiveMigration().build()

    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val duplicateDao = db.duplicateDao()
    private val unrecognizedDao = db.unrecognizedMessageDao()

    private val smsParser = SmsParserEngine()
    private val receiptParser = ReceiptParserEngine()
    private val reconciler = ReconciliationEngine()

    val transactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val accounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val categories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val unrecognizedMessages: Flow<List<UnrecognizedMessageEntity>> = unrecognizedDao.getActiveMessages()

    val duplicateCandidates: Flow<List<DuplicateCandidateItem>> = combine(
        duplicateDao.getAllDuplicates(),
        transactionDao.getAllTransactions()
    ) { duplicates, txList ->
        val txMap = txList.associateBy { it.id }
        duplicates.mapNotNull { dup ->
            val existing = txMap[dup.existingTransactionId] ?: return@mapNotNull null
            val imported = TransactionEntity(
                id = UUID.randomUUID().toString(),
                externalRef = dup.importedExternalRef,
                accountId = dup.importedAccountId,
                categoryId = dup.importedCategoryId,
                amount = dup.importedAmount,
                currency = dup.importedCurrency,
                direction = dup.importedDirection,
                type = dup.importedType,
                timestamp = dup.importedTimestamp,
                counterparty = dup.importedCounterparty,
                sourceMethod = dup.importedSourceMethod,
                notes = dup.importedNotes,
                receiptImagePath = dup.importedReceiptImagePath,
                isVerified = true,
                isDeleted = false,
                accountRail = dup.importedAccountRail
            )
            DuplicateCandidateItem(
                id = dup.id,
                existingTransaction = existing,
                importedTransaction = imported,
                matchScore = dup.matchScore,
                matchFactors = dup.matchFactors.split(";").filter { it.isNotBlank() }
            )
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            checkAndSeedInitialData()
        }
    }

    suspend fun checkAndSeedInitialData() {
        val existingAccounts = accountDao.getAllAccountsList()
        if (existingAccounts.isEmpty()) {
            accountDao.insertAccounts(SeedData.getDefaultAccounts())
            categoryDao.insertCategories(SeedData.getDefaultCategories())
            transactionDao.insertTransactions(SeedData.getDefaultTransactions())
            duplicateDao.insertDuplicates(SeedData.getDefaultDuplicates())
            unrecognizedDao.insertAll(SeedData.getDefaultUnrecognizedMessages())
        }
    }

    suspend fun resetToDemoData() = withContext(Dispatchers.IO) {
        transactionDao.clearAll()
        accountDao.clearAll()
        categoryDao.clearAll()
        duplicateDao.clearAll()
        unrecognizedDao.clearAll()

        accountDao.insertAccounts(SeedData.getDefaultAccounts())
        categoryDao.insertCategories(SeedData.getDefaultCategories())
        transactionDao.insertTransactions(SeedData.getDefaultTransactions())
        duplicateDao.insertDuplicates(SeedData.getDefaultDuplicates())
        unrecognizedDao.insertAll(SeedData.getDefaultUnrecognizedMessages())
    }

    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        transactionDao.clearAll()
        duplicateDao.clearAll()
        unrecognizedDao.clearAll()
    }

    // --- Transactions ---
    suspend fun addTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.insertTransaction(transaction)
        // Update account balance
        val account = accountDao.getAccountById(transaction.accountId)
        if (account != null) {
            val newBalance = account.currentBalance + transaction.amount
            accountDao.updateBalance(account.id, newBalance)
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val oldTx = transactionDao.getTransactionById(transaction.id)
        transactionDao.updateTransaction(transaction)

        if (oldTx != null) {
            if (oldTx.accountId == transaction.accountId) {
                val delta = transaction.amount - oldTx.amount
                if (delta != 0.0) {
                    val acc = accountDao.getAccountById(transaction.accountId)
                    if (acc != null) {
                        accountDao.updateBalance(acc.id, acc.currentBalance + delta)
                    }
                }
            } else {
                // Revert from previous account, apply to new account
                val oldAcc = accountDao.getAccountById(oldTx.accountId)
                if (oldAcc != null) {
                    accountDao.updateBalance(oldAcc.id, oldAcc.currentBalance - oldTx.amount)
                }
                val newAcc = accountDao.getAccountById(transaction.accountId)
                if (newAcc != null) {
                    accountDao.updateBalance(newAcc.id, newAcc.currentBalance + transaction.amount)
                }
            }
        }
    }

    suspend fun deleteTransaction(id: String) = withContext(Dispatchers.IO) {
        val existing = transactionDao.getTransactionById(id)
        if (existing != null) {
            transactionDao.softDelete(id)
            val account = accountDao.getAccountById(existing.accountId)
            if (account != null) {
                val adjustedBalance = account.currentBalance - existing.amount
                accountDao.updateBalance(account.id, adjustedBalance)
            }
        }
    }

    // --- Accounts ---
    suspend fun addAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(id: String) = withContext(Dispatchers.IO) {
        accountDao.softDelete(id)
    }

    // --- Categories ---
    suspend fun addCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategory(id: String) = withContext(Dispatchers.IO) {
        categoryDao.softDelete(id)
    }

    // --- Ingestion Pipeline ---
    suspend fun ingestSms(
        sender: String,
        body: String,
        overrideAccountId: String? = null,
        overrideCategoryId: String? = null
    ): Pair<ReconciliationOutcome, String> = withContext(Dispatchers.IO) {
        val parsed = smsParser.parse(sender, body)
        if (parsed.isFinancial) {
            val accountsList = accountDao.getAllAccountsList()
            val targetAccount: AccountEntity = (if (!overrideAccountId.isNullOrBlank()) {
                accountsList.firstOrNull { it.id == overrideAccountId }
            } else null) ?: when {
                parsed.provider.contains("Telecel", ignoreCase = true) ->
                    accountsList.firstOrNull { it.name.contains("Telecel", ignoreCase = true) || it.name.contains("Vodafone", ignoreCase = true) }
                parsed.provider.contains("MoMo", ignoreCase = true) || parsed.provider.contains("MTN", ignoreCase = true) ->
                    accountsList.firstOrNull { it.name.contains("MTN", ignoreCase = true) || it.accountType.equals("MOMO", ignoreCase = true) }
                parsed.provider.contains("Bank", ignoreCase = true) || parsed.provider.contains("Ecobank", ignoreCase = true) || parsed.provider.contains("Stanbic", ignoreCase = true) ->
                    accountsList.firstOrNull { it.accountType.equals("BANK", ignoreCase = true) || it.name.contains(parsed.provider, ignoreCase = true) }
                else -> null
            } ?: accountsList.firstOrNull { it.accountType.equals("MOMO", ignoreCase = true) }
              ?: accountsList.firstOrNull()
              ?: AccountEntity(id = "acc-default", name = "Default Wallet", accountType = "MOMO", currentBalance = 0.0)

            val categoriesList = categoryDao.getAllCategoriesList()
            val cat: CategoryEntity = (if (!overrideCategoryId.isNullOrBlank()) {
                categoriesList.firstOrNull { it.id == overrideCategoryId }
            } else null) ?: autoCategorize(parsed.counterparty, parsed.rawBody, categoriesList)

            val candidateTx = parsed.toTransactionEntity(
                accountId = targetAccount.id,
                accountRail = targetAccount.name,
                categoryId = cat.id,
                categoryName = cat.name,
                categoryColor = cat.colorHex
            )

            val existingList = transactionDao.getAllTransactionsList()
            val eval = reconciler.evaluate(candidateTx, existingList)

            when (eval.outcome) {
                ReconciliationOutcome.IDEMPOTENT_SKIP -> {
                    Pair(ReconciliationOutcome.IDEMPOTENT_SKIP, "Ignored exact duplicate (Ref: ${candidateTx.externalRef})")
                }
                ReconciliationOutcome.AUTO_MERGED -> {
                    eval.resolvedTransaction?.let { transactionDao.updateTransaction(it) }
                    Pair(ReconciliationOutcome.AUTO_MERGED, "Auto-merged with existing record (${candidateTx.counterparty})")
                }
                ReconciliationOutcome.QUEUED_FOR_REVIEW -> {
                    eval.candidateForQueue?.let { cand ->
                        val existingDuplicates = duplicateDao.getAllDuplicatesList()
                        val alreadyQueued = existingDuplicates.any {
                            it.existingTransactionId == cand.existingTransactionId &&
                            !it.importedExternalRef.isNullOrBlank() &&
                            it.importedExternalRef.equals(cand.importedExternalRef, ignoreCase = true)
                        }
                        if (!alreadyQueued) {
                            duplicateDao.insertDuplicate(cand)
                        }
                    }
                    Pair(ReconciliationOutcome.QUEUED_FOR_REVIEW, "Queued to Review Queue (${eval.matchScore}% match)")
                }
                ReconciliationOutcome.INSERTED_NEW -> {
                    transactionDao.insertTransaction(candidateTx)
                    // If ending balance was extracted from official SMS, sync account balance to it; otherwise apply delta
                    val updatedBal = if (parsed.endingBalance != null && parsed.endingBalance >= 0) {
                        parsed.endingBalance
                    } else {
                        targetAccount.currentBalance + candidateTx.amount
                    }
                    accountDao.updateBalance(targetAccount.id, updatedBal)
                    Pair(ReconciliationOutcome.INSERTED_NEW, "Ingested: ${candidateTx.currency} ${String.format("%.2f", kotlin.math.abs(candidateTx.amount))} to ${candidateTx.counterparty}")
                }
            }
        } else {
            // Filter out promotional ads, marketing solicitations, and unknown third-party messages completely.
            // Only queue legitimate unparsed messages from authorized financial senders to the Unrecognized Inbox for manual review.
            val isAuthorizedRail = smsParser.isAuthorizedFinancialSender(sender, body)
            val isPromo = smsParser.isPromotionalOrManagementMessage(body, sender)

            if (isAuthorizedRail && !isPromo) {
                val lower = body.lowercase(Locale.ROOT)
                if (lower.contains("money") || lower.contains("ghs") || lower.contains("gh¢") || lower.contains("payment") || lower.contains("debit") || lower.contains("credit")) {
                    val unrec = UnrecognizedMessageEntity(
                        id = "unrec-${System.currentTimeMillis()}",
                        sender = sender,
                        rawBody = body,
                        timestamp = System.currentTimeMillis()
                    )
                    unrecognizedDao.insert(unrec)
                    return@withContext Pair(ReconciliationOutcome.QUEUED_FOR_REVIEW, "Alert saved to Unrecognized Inbox for manual review")
                }
            }
            Pair(ReconciliationOutcome.IDEMPOTENT_SKIP, parsed.rejectionReason ?: "Non-financial or promotional message filtered out")
        }
    }

    suspend fun parseReceipt(rawText: String): ReceiptDraft = withContext(Dispatchers.Default) {
        receiptParser.parse(rawText)
    }

    private fun autoCategorize(counterparty: String, rawBody: String = "", categories: List<CategoryEntity>): CategoryEntity {
        val lower = "${counterparty.lowercase(Locale.ROOT)} ${rawBody.lowercase(Locale.ROOT)}"
        for (cat in categories) {
            val cName = cat.name.lowercase(Locale.ROOT)
            if (lower.contains("food") || lower.contains("restaurant") || lower.contains("chop") || lower.contains("inn") || lower.contains("kfc") || lower.contains("buka") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("breakfast") || lower.contains("waakye") || lower.contains("fufu") || lower.contains("pizza") || lower.contains("shawarma") || lower.contains("canteen")) {
                if (cName.contains("food") || cName.contains("dining")) return cat
            }
            if (lower.contains("bolt") || lower.contains("uber") || lower.contains("fuel") || lower.contains("shell") || lower.contains("total") || lower.contains("goil") || lower.contains("yango") || lower.contains("ride") || lower.contains("taxi")) {
                if (cName.contains("transport") || cName.contains("fuel")) return cat
            }
            if (lower.contains("ecg") || lower.contains("water") || lower.contains("fibre") || lower.contains("dstv") || lower.contains("power") || lower.contains("gotv") || lower.contains("airtime") || lower.contains("internet") || lower.contains("bundle")) {
                if (cName.contains("bills") || cName.contains("utilities")) return cat
            }
            if (lower.contains("mart") || lower.contains("grocer") || lower.contains("shop") || lower.contains("compughana") || lower.contains("market") || lower.contains("supermarket")) {
                if (cName.contains("shopping") || cName.contains("groceries")) return cat
            }
            if (lower.contains("gift") || lower.contains("tip") || lower.contains("donation") || lower.contains("tithe") || lower.contains("offering") || lower.contains("dash") || lower.contains("support")) {
                if (cName.contains("gift") || cName.contains("giving")) return cat
            }
            if (lower.contains("salary") || lower.contains("retainer") || lower.contains("client") || lower.contains("payout") || lower.contains("dividends")) {
                if (cName.contains("income")) return cat
            }
        }
        return categories.firstOrNull { it.name.contains("Food", ignoreCase = true) }
            ?: categories.firstOrNull()
            ?: CategoryEntity(id = "cat-default", name = "General", icon = "briefcase", colorHex = "#3B82F6")
    }

    // --- Duplicate Queue Actions ---
    suspend fun addDuplicate(duplicate: DuplicateEntity) = withContext(Dispatchers.IO) {
        val existingDuplicates = duplicateDao.getAllDuplicatesList()
        val alreadyQueued = existingDuplicates.any {
            it.existingTransactionId == duplicate.existingTransactionId &&
            !it.importedExternalRef.isNullOrBlank() &&
            it.importedExternalRef.equals(duplicate.importedExternalRef, ignoreCase = true)
        }
        if (!alreadyQueued) {
            duplicateDao.insertDuplicate(duplicate)
        }
    }

    suspend fun mergeDuplicate(item: DuplicateCandidateItem) = withContext(Dispatchers.IO) {
        val merged = item.existingTransaction.copy(
            receiptImagePath = item.importedTransaction.receiptImagePath ?: item.existingTransaction.receiptImagePath,
            notes = if (item.existingTransaction.notes.isNullOrBlank()) item.importedTransaction.notes else item.existingTransaction.notes,
            isVerified = true
        )
        transactionDao.updateTransaction(merged)
        duplicateDao.deleteById(item.id)
    }

    suspend fun keepBothDuplicate(item: DuplicateCandidateItem) = withContext(Dispatchers.IO) {
        transactionDao.insertTransaction(item.importedTransaction)
        val acc = accountDao.getAccountById(item.importedTransaction.accountId)
        if (acc != null) {
            accountDao.updateBalance(acc.id, acc.currentBalance + item.importedTransaction.amount)
        }
        duplicateDao.deleteById(item.id)
    }

    suspend fun dismissDuplicate(id: String) = withContext(Dispatchers.IO) {
        duplicateDao.deleteById(id)
    }

    // --- Unrecognized Messages ---
    suspend fun resolveUnrecognized(id: String, convertedTx: TransactionEntity?) = withContext(Dispatchers.IO) {
        if (convertedTx != null) {
            transactionDao.insertTransaction(convertedTx)
            val acc = accountDao.getAccountById(convertedTx.accountId)
            if (acc != null) {
                accountDao.updateBalance(acc.id, acc.currentBalance + convertedTx.amount)
            }
        }
        unrecognizedDao.markResolved(id)
    }

    suspend fun dismissUnrecognized(id: String) = withContext(Dispatchers.IO) {
        unrecognizedDao.deleteById(id)
    }

    // --- Export CSV ---
    suspend fun exportCsv(transactionsList: List<TransactionEntity>): String = withContext(Dispatchers.Default) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Merchant / Payee,Category,Amount,Currency,Type,Date,Time,Account Rail,Reference,Notes\n")
        for (tx in transactionsList) {
            val dateStr = dateFormat.format(Date(tx.timestamp))
            val timeStr = timeFormat.format(Date(tx.timestamp))
            val merchant = tx.counterparty.replace("\"", "\"\"")
            val cat = (tx.categoryName ?: tx.categoryId).replace("\"", "\"\"")
            val notes = (tx.notes ?: "").replace("\"", "\"\"")
            val ref = tx.externalRef ?: ""
            sb.append("\"${tx.id}\",\"$merchant\",\"$cat\",${tx.amount},\"${tx.currency}\",\"${tx.type}\",\"$dateStr\",\"$timeStr\",\"${tx.accountRail ?: ""}\",\"$ref\",\"$notes\"\n")
        }
        sb.toString()
    }

    // --- Export Encrypted Backup ---
    suspend fun exportEncryptedBackup(passphrase: String): String = withContext(Dispatchers.IO) {
        val txs = transactionDao.getAllTransactionsList()
        val accs = accountDao.getAllAccountsList()
        val cats = categoryDao.getAllCategoriesList()

        val root = JSONObject()
        root.put("app", "Folio Smart Expense Tracker")
        root.put("version", "1.0.0")
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))

        val txArray = JSONArray()
        for (t in txs) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("externalRef", t.externalRef)
            obj.put("accountId", t.accountId)
            obj.put("categoryId", t.categoryId)
            obj.put("amount", t.amount)
            obj.put("currency", t.currency)
            obj.put("direction", t.direction)
            obj.put("type", t.type)
            obj.put("timestamp", t.timestamp)
            obj.put("counterparty", t.counterparty)
            obj.put("sourceMethod", t.sourceMethod)
            obj.put("notes", t.notes)
            obj.put("isVerified", t.isVerified)
            obj.put("categoryName", t.categoryName)
            obj.put("categoryColor", t.categoryColor)
            obj.put("accountRail", t.accountRail)
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val accArray = JSONArray()
        for (a in accs) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("name", a.name)
            obj.put("accountType", a.accountType)
            obj.put("currentBalance", a.currentBalance)
            obj.put("currency", a.currency)
            accArray.put(obj)
        }
        root.put("accounts", accArray)

        val catArray = JSONArray()
        for (c in cats) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("icon", c.icon)
            obj.put("colorHex", c.colorHex)
            obj.put("budgetLimit", c.budgetLimit ?: JSONObject.NULL)
            catArray.put(obj)
        }
        root.put("categories", catArray)

        val rawJson = root.toString()

        // PBKDF2WithHmacSHA256 key derivation + AES-256-GCM authenticated encryption
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val iterations = 100_000
        val keySpec = PBEKeySpec(passphrase.toCharArray(), saltBytes, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

        val ivBytes = ByteArray(12) // 12-byte IV standard for AES-GCM
        SecureRandom().nextBytes(ivBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedBytes = cipher.doFinal(rawJson.toByteArray(StandardCharsets.UTF_8))

        val resultObj = JSONObject()
        resultObj.put("version", 2)
        resultObj.put("cipher", "AES-256-GCM")
        resultObj.put("kdf", "PBKDF2WithHmacSHA256")
        resultObj.put("iterations", iterations)
        resultObj.put("salt", Base64.encodeToString(saltBytes, Base64.NO_WRAP))
        resultObj.put("iv", Base64.encodeToString(ivBytes, Base64.NO_WRAP))
        resultObj.put("data", Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
        resultObj.toString()
    }

    // --- Import Encrypted Backup (Supports v2 AES-256-GCM and legacy v1 AES-CBC) ---
    suspend fun importEncryptedBackup(encryptedPayload: String, passphrase: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pkg = JSONObject(encryptedPayload)
            val jsonString = if (pkg.has("salt") || pkg.optInt("version", 1) >= 2) {
                // Version 2: PBKDF2WithHmacSHA256 + AES-256-GCM
                val saltBytes = Base64.decode(pkg.getString("salt"), Base64.DEFAULT)
                val ivBytes = Base64.decode(pkg.getString("iv"), Base64.DEFAULT)
                val dataBytes = Base64.decode(pkg.getString("data"), Base64.DEFAULT)
                val iterations = pkg.optInt("iterations", 100_000)

                val keySpec = PBEKeySpec(passphrase.toCharArray(), saltBytes, iterations, 256)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                val decryptedBytes = cipher.doFinal(dataBytes)
                String(decryptedBytes, StandardCharsets.UTF_8)
            } else {
                // Version 1 Legacy Fallback: AES-CBC with padded passphrase
                val ivBytes = Base64.decode(pkg.getString("iv"), Base64.DEFAULT)
                val dataBytes = Base64.decode(pkg.getString("data"), Base64.DEFAULT)
                val keyBytes = passphrase.padEnd(32, '*').substring(0, 32).toByteArray(StandardCharsets.UTF_8)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                val keySpec = SecretKeySpec(keyBytes, "AES")
                val ivSpec = IvParameterSpec(ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val decryptedBytes = cipher.doFinal(dataBytes)
                String(decryptedBytes, StandardCharsets.UTF_8)
            }

            val root = JSONObject(jsonString)

            if (root.has("accounts")) {
                val accList = mutableListOf<AccountEntity>()
                val accArray = root.getJSONArray("accounts")
                for (i in 0 until accArray.length()) {
                    val obj = accArray.getJSONObject(i)
                    accList.add(
                        AccountEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            accountType = obj.optString("accountType", "MOMO"),
                            currentBalance = obj.optDouble("currentBalance", 0.0),
                            currency = obj.optString("currency", "GHS")
                        )
                    )
                }
                accountDao.clearAll()
                accountDao.insertAccounts(accList)
            }

            if (root.has("categories")) {
                val catList = mutableListOf<CategoryEntity>()
                val catArray = root.getJSONArray("categories")
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    catList.add(
                        CategoryEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            icon = obj.optString("icon", "briefcase"),
                            colorHex = obj.optString("colorHex", "#3B82F6"),
                            budgetLimit = if (obj.isNull("budgetLimit")) null else obj.getDouble("budgetLimit")
                        )
                    )
                }
                categoryDao.clearAll()
                categoryDao.insertCategories(catList)
            }

            if (root.has("transactions")) {
                val txList = mutableListOf<TransactionEntity>()
                val txArray = root.getJSONArray("transactions")
                for (i in 0 until txArray.length()) {
                    val obj = txArray.getJSONObject(i)
                    txList.add(
                        TransactionEntity(
                            id = obj.getString("id"),
                            externalRef = if (obj.isNull("externalRef")) null else obj.getString("externalRef"),
                            accountId = obj.getString("accountId"),
                            categoryId = obj.getString("categoryId"),
                            amount = obj.getDouble("amount"),
                            currency = obj.optString("currency", "GHS"),
                            direction = obj.optString("direction", "DEBIT"),
                            type = obj.optString("type", "Expense"),
                            timestamp = obj.getLong("timestamp"),
                            counterparty = obj.getString("counterparty"),
                            sourceMethod = obj.optString("sourceMethod", "manual"),
                            notes = if (obj.isNull("notes")) null else obj.getString("notes"),
                            isVerified = obj.optBoolean("isVerified", true),
                            categoryName = if (obj.isNull("categoryName")) null else obj.getString("categoryName"),
                            categoryColor = if (obj.isNull("categoryColor")) null else obj.getString("categoryColor"),
                            accountRail = if (obj.isNull("accountRail")) null else obj.getString("accountRail")
                        )
                    )
                }
                transactionDao.clearAll()
                transactionDao.insertTransactions(txList)
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
