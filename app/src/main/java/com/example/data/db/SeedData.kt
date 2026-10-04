package com.example.data.db

import com.example.data.model.AccountEntity
import com.example.data.model.AccountType
import com.example.data.model.CategoryEntity
import com.example.data.model.DuplicateEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UnrecognizedMessageEntity
import java.util.Calendar

object SeedData {

    fun getDefaultAccounts(): List<AccountEntity> = listOf(
        AccountEntity(
            id = "acc-momo",
            name = "MTN Mobile Money",
            accountType = AccountType.MOMO.name,
            currentBalance = 4250.00,
            currency = "GHS"
        ),
        AccountEntity(
            id = "acc-bank",
            name = "Stanbic Bank Checking",
            accountType = AccountType.BANK.name,
            currentBalance = 18450.00,
            currency = "GHS"
        ),
        AccountEntity(
            id = "acc-card",
            name = "Visa Debit Card",
            accountType = AccountType.CARD.name,
            currentBalance = 1200.00,
            currency = "GHS"
        ),
        AccountEntity(
            id = "acc-cash",
            name = "Physical Cash",
            accountType = AccountType.CASH.name,
            currentBalance = 350.00,
            currency = "GHS"
        )
    )

    fun getDefaultCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(
            id = "cat-income",
            name = "Income & Inflows",
            icon = "coins",
            colorHex = "#10B981",
            budgetLimit = null,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-food",
            name = "Food & Dining",
            icon = "utensils",
            colorHex = "#F59E0B",
            budgetLimit = 2500.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-transport",
            name = "Transport & Fuel",
            icon = "car",
            colorHex = "#3B82F6",
            budgetLimit = 1200.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-utilities",
            name = "Bills & Utilities",
            icon = "zap",
            colorHex = "#8B5CF6",
            budgetLimit = 1500.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-shopping",
            name = "Shopping & Groceries",
            icon = "shopping-bag",
            colorHex = "#EC4899",
            budgetLimit = 3000.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-entertainment",
            name = "Entertainment & Leisure",
            icon = "film",
            colorHex = "#6366F1",
            budgetLimit = 800.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-gifts",
            name = "Gifts, Tips & Giving",
            icon = "heart",
            colorHex = "#E11D48",
            budgetLimit = 600.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-business",
            name = "Business & Office",
            icon = "briefcase",
            colorHex = "#14B8A6",
            budgetLimit = 4000.00,
            isDefault = true
        ),
        CategoryEntity(
            id = "cat-health",
            name = "Health & Fitness",
            icon = "heart-pulse",
            colorHex = "#EF4444",
            budgetLimit = 600.00,
            isDefault = true
        )
    )

    private fun dateInThisMonth(day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val clampedDay = day.coerceIn(1, maxDays)
        cal.set(Calendar.DAY_OF_MONTH, clampedDay)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dateInLastMonth(day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val clampedDay = day.coerceIn(1, maxDays)
        cal.set(Calendar.DAY_OF_MONTH, clampedDay)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getDefaultTransactions(): List<TransactionEntity> {
        return listOf(
            TransactionEntity(
                id = "tx-1",
                externalRef = "MOMO-992102-GH",
                accountId = "acc-momo",
                accountRail = "MTN Mobile Money",
                categoryId = "cat-income",
                categoryName = "Income & Inflows",
                categoryColor = "#10B981",
                amount = 14500.00,
                currency = "GHS",
                direction = "CREDIT",
                type = "Income",
                timestamp = dateInThisMonth(2, 9, 30),
                counterparty = "Acme Global Client Retainer",
                sourceMethod = "sms",
                notes = "Monthly software architecture retainer payout"
            ),
            TransactionEntity(
                id = "tx-2",
                externalRef = "POS-882190",
                accountId = "acc-card",
                accountRail = "Visa Debit Card",
                categoryId = "cat-transport",
                categoryName = "Transport & Fuel",
                categoryColor = "#3B82F6",
                amount = -450.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(3, 14, 15),
                counterparty = "TotalEnergies Cantonments",
                sourceMethod = "sms",
                notes = "V-Power fuel refill"
            ),
            TransactionEntity(
                id = "tx-3",
                externalRef = "MOMO-881290",
                accountId = "acc-momo",
                accountRail = "MTN Mobile Money",
                categoryId = "cat-shopping",
                categoryName = "Shopping & Groceries",
                categoryColor = "#EC4899",
                amount = -820.50,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(4, 18, 20),
                counterparty = "MaxMart Supermarket East Legon",
                sourceMethod = "sms",
                notes = "Household provisions & groceries"
            ),
            TransactionEntity(
                id = "tx-4",
                externalRef = "ECG-PRE-2024",
                accountId = "acc-momo",
                accountRail = "MTN Mobile Money",
                categoryId = "cat-utilities",
                categoryName = "Bills & Utilities",
                categoryColor = "#8B5CF6",
                amount = -300.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(6, 11, 5),
                counterparty = "ECG Power Prepaid",
                sourceMethod = "sms",
                notes = "Residential meter recharge"
            ),
            TransactionEntity(
                id = "tx-5",
                accountId = "acc-cash",
                accountRail = "Physical Cash",
                categoryId = "cat-food",
                categoryName = "Food & Dining",
                categoryColor = "#F59E0B",
                amount = -280.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(8, 13, 40),
                counterparty = "Buka Restaurant Osu",
                sourceMethod = "manual",
                notes = "Team lunch & jollof rice"
            ),
            TransactionEntity(
                id = "tx-6",
                externalRef = "FIGMA-SUB-01",
                accountId = "acc-card",
                accountRail = "Visa Debit Card",
                categoryId = "cat-business",
                categoryName = "Business & Office",
                categoryColor = "#14B8A6",
                amount = -150.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(10, 8, 12),
                counterparty = "Figma Design Subscription",
                sourceMethod = "card",
                notes = "Design system license"
            ),
            TransactionEntity(
                id = "tx-7",
                externalRef = "BOLT-RIDE-GH",
                accountId = "acc-momo",
                accountRail = "MTN Mobile Money",
                categoryId = "cat-transport",
                categoryName = "Transport & Fuel",
                categoryColor = "#3B82F6",
                amount = -65.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(11, 21, 30),
                counterparty = "Bolt Ride Airport Accra",
                sourceMethod = "sms"
            ),
            TransactionEntity(
                id = "tx-8",
                externalRef = "MTN-FIBRE-09",
                accountId = "acc-momo",
                accountRail = "MTN Mobile Money",
                categoryId = "cat-utilities",
                categoryName = "Bills & Utilities",
                categoryColor = "#8B5CF6",
                amount = -495.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(14, 10, 0),
                counterparty = "MTN Fibre Broadband",
                sourceMethod = "sms",
                notes = "100Mbps unlimited fibre plan"
            ),
            TransactionEntity(
                id = "tx-9",
                externalRef = "BNK-STAN-99",
                accountId = "acc-bank",
                accountRail = "Stanbic Bank Checking",
                categoryId = "cat-business",
                categoryName = "Business & Office",
                categoryColor = "#14B8A6",
                amount = -1600.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(15, 9, 0),
                counterparty = "Coworking Hub Cantonments",
                sourceMethod = "sms",
                notes = "Dedicated desk monthly rental"
            ),
            TransactionEntity(
                id = "tx-10",
                accountId = "acc-card",
                accountRail = "Visa Debit Card",
                categoryId = "cat-shopping",
                categoryName = "Shopping & Groceries",
                categoryColor = "#EC4899",
                amount = -1080.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInThisMonth(12, 15, 15),
                counterparty = "CompuGhana Electronics",
                sourceMethod = "ocr",
                notes = "USB-C Hub and ergonomic mouse"
            ),
            // Previous month baseline data
            TransactionEntity(
                id = "tx-prev-1",
                externalRef = "MOMO-PREV-01",
                accountId = "acc-momo",
                accountRail = "MTN Mobile Money",
                categoryId = "cat-income",
                categoryName = "Income & Inflows",
                categoryColor = "#10B981",
                amount = 10000.00,
                currency = "GHS",
                direction = "CREDIT",
                type = "Income",
                timestamp = dateInLastMonth(24, 10, 42),
                counterparty = "Acme Studio Retainer",
                sourceMethod = "sms"
            ),
            TransactionEntity(
                id = "tx-prev-2",
                accountId = "acc-bank",
                accountRail = "Stanbic Bank Checking",
                categoryId = "cat-business",
                categoryName = "Business & Office",
                categoryColor = "#14B8A6",
                amount = -2480.00,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = dateInLastMonth(15, 9, 0),
                counterparty = "Workspace & Supplies",
                sourceMethod = "sms"
            )
        )
    }

    fun getDefaultDuplicates(): List<DuplicateEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            DuplicateEntity(
                id = "dup-figma",
                existingTransactionId = "tx-6",
                importedExternalRef = "OCR-FIG-SCAN",
                importedAccountId = "acc-card",
                importedCategoryId = "cat-business",
                importedAmount = -150.00,
                importedCurrency = "GHS",
                importedDirection = "DEBIT",
                importedType = "Expense",
                importedTimestamp = now - 3600000,
                importedCounterparty = "Figma Design Subscription",
                importedSourceMethod = "ocr",
                importedNotes = "Scanned invoice receipt",
                importedReceiptImagePath = "receipt_figma.jpg",
                importedAccountRail = "Visa Debit Card",
                matchScore = 98,
                matchFactors = "Exact amount match (GH₵ 150.00);Same account rail (Visa Debit Card);Within 1 minute timestamp;Identical merchant normalized key"
            ),
            DuplicateEntity(
                id = "dup-ride",
                existingTransactionId = "tx-7",
                importedExternalRef = "MOMO-RIDE-901",
                importedAccountId = "acc-momo",
                importedCategoryId = "cat-transport",
                importedAmount = -65.00,
                importedCurrency = "GHS",
                importedDirection = "DEBIT",
                importedType = "Expense",
                importedTimestamp = now - 7200000,
                importedCounterparty = "Bolt Ride Airport Accra",
                importedSourceMethod = "sms",
                importedNotes = "Payment made for GH₵ 65.00",
                importedReceiptImagePath = null,
                importedAccountRail = "MTN Mobile Money",
                matchScore = 85,
                matchFactors = "Exact amount match (GH₵ 65.00);Within 2 minutes timestamp;Related transport category"
            )
        )
    }

    fun getDefaultUnrecognizedMessages(): List<UnrecognizedMessageEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            UnrecognizedMessageEntity(
                id = "unrec-1",
                sender = "Ecobank GH",
                rawBody = "ECB Alert: Your acct has been debited by GHS 240.00 on 24/09/26 at POS 8820 ACCRA. Avail bal GHS 3,420.50.",
                timestamp = now - (4 * 3600 * 1000),
                suspectedAmount = "240.00",
                suspectedMerchant = "POS 8820 ACCRA"
            ),
            UnrecognizedMessageEntity(
                id = "unrec-2",
                sender = "+233244000111",
                rawBody = "Payment received for design consultation GH₵ 450. Please confirm. Thanks, Kweku.",
                timestamp = now - (24 * 3600 * 1000),
                suspectedAmount = "450.00",
                suspectedMerchant = "Kweku"
            )
        )
    }
}
