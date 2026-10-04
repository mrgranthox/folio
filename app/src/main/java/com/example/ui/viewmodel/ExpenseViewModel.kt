package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatMessage
import com.example.data.ai.GeminiChatService
import com.example.data.engine.ReceiptDraft
import com.example.data.engine.ReconciliationEngine
import com.example.data.engine.ReconciliationOutcome
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.DuplicateCandidateItem
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.model.UnrecognizedMessageEntity
import com.example.data.repository.ExpenseRepository
import com.example.ui.components.CurrencyUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class DatePeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_90_DAYS("Last 90 Days"),
    ALL_TIME("All Time")
}

enum class SortOrder(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    AMOUNT_DESC("Highest Amount"),
    AMOUNT_ASC("Lowest Amount")
}

data class CategorySpendSummary(
    val category: CategoryEntity,
    val totalSpend: Double,
    val percentageOfTotal: Double,
    val budgetLimit: Double?,
    val budgetProgress: Double,
    val isOverBudget: Boolean
)

data class DailyCashFlowBar(
    val label: String,
    val dateTimestamp: Long,
    val totalIncome: Double,
    val totalExpense: Double
)

data class ExpenseUiState(
    val allTransactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val duplicateCandidates: List<DuplicateCandidateItem> = emptyList(),
    val unrecognizedMessages: List<UnrecognizedMessageEntity> = emptyList(),
    // Expense Tracking Metrics (Tracking expenses & transactions, not bank balances)
    val totalMonthlySpent: Double = 0.0,
    val totalTransactionsCount: Int = 0,
    val thisMonthInflow: Double = 0.0,
    val thisMonthOutflow: Double = 0.0,
    val thisMonthNet: Double = 0.0,
    val lastMonthInflow: Double = 0.0,
    val lastMonthOutflow: Double = 0.0,
    val inflowVariancePct: Double = 0.0,
    val outflowVariancePct: Double = 0.0,
    val totalMonthlyBudget: Double = 0.0,
    val monthlyBudgetUsedPct: Double = 0.0,
    val accountSpendMap: Map<String, Double> = emptyMap(),
    // Filters
    val searchQuery: String = "",
    val selectedPeriod: DatePeriod = DatePeriod.THIS_MONTH,
    val selectedCategoryFilter: String? = null,
    val selectedAccountFilter: String? = null,
    val selectedDirectionFilter: TransactionDirection? = null,
    val selectedSort: SortOrder = SortOrder.DATE_DESC,
    // Analytics
    val categorySpendList: List<CategorySpendSummary> = emptyList(),
    val dailyCashFlow: List<DailyCashFlowBar> = emptyList(),
    val topSpendingMerchant: String = "None",
    val topSpendAmount: Double = 0.0,
    val avgDailyExpense: Double = 0.0,
    val preferredCurrency: String = "GHS",
    val isLoading: Boolean = false
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)
    private val reconciler = ReconciliationEngine()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedPeriod = MutableStateFlow(DatePeriod.THIS_MONTH)
    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    private val _selectedAccountFilter = MutableStateFlow<String?>(null)
    private val _selectedDirectionFilter = MutableStateFlow<TransactionDirection?>(null)
    private val _selectedSort = MutableStateFlow(SortOrder.DATE_DESC)

    // Event bus for notifications / snackbars
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    val uiState: StateFlow<ExpenseUiState> = combine(
        repository.transactions,
        repository.accounts,
        repository.categories,
        repository.duplicateCandidates,
        repository.unrecognizedMessages,
        _searchQuery,
        _selectedPeriod,
        _selectedCategoryFilter,
        _selectedAccountFilter,
        _selectedDirectionFilter,
        _selectedSort
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val transactions = args[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = args[1] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = args[2] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val duplicates = args[3] as List<DuplicateCandidateItem>
        @Suppress("UNCHECKED_CAST")
        val unrecognized = args[4] as List<UnrecognizedMessageEntity>
        val search = args[5] as String
        val period = args[6] as DatePeriod
        val catFilter = args[7] as String?
        val accFilter = args[8] as String?
        val dirFilter = args[9] as TransactionDirection?
        val sort = args[10] as SortOrder

        // Compute period timestamps
        val now = Calendar.getInstance()
        val currentMonthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastMonthStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val last90DaysStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -90)
        }.timeInMillis

        // Month-to-date calculations
        val thisMonthTransactions = transactions.filter { it.timestamp >= currentMonthStart }
        val lastMonthTransactions = transactions.filter { it.timestamp in lastMonthStart until currentMonthStart }

        val thisMonthInflow = thisMonthTransactions.filter { it.amount > 0 }.sumOf { it.amount }
        val thisMonthOutflow = abs(thisMonthTransactions.filter { it.amount < 0 }.sumOf { it.amount })
        val thisMonthNet = thisMonthTransactions.sumOf { it.amount }

        val lastMonthInflow = lastMonthTransactions.filter { it.amount > 0 }.sumOf { it.amount }
        val lastMonthOutflow = abs(lastMonthTransactions.filter { it.amount < 0 }.sumOf { it.amount })

        val inflowVariancePct = if (lastMonthInflow > 0) {
            ((thisMonthInflow - lastMonthInflow) / lastMonthInflow) * 100.0
        } else 0.0

        val outflowVariancePct = if (lastMonthOutflow > 0) {
            ((thisMonthOutflow - lastMonthOutflow) / lastMonthOutflow) * 100.0
        } else 0.0

        // Total Budget
        val totalMonthlyBudget = categories.mapNotNull { it.budgetLimit }.sum()
        val budgetUsedPct = if (totalMonthlyBudget > 0) (thisMonthOutflow / totalMonthlyBudget) else 0.0

        // Account spent map (tracks how much was spent from each payment method/rail)
        val accountSpendMap = mutableMapOf<String, Double>()
        for (acc in accounts) {
            val spent = abs(transactions.filter { it.accountId == acc.id && it.amount < 0 }.sumOf { it.amount })
            accountSpendMap[acc.id] = spent
        }

        // Apply filters
        var filtered = transactions

        // Period filter
        filtered = when (period) {
            DatePeriod.THIS_MONTH -> filtered.filter { it.timestamp >= currentMonthStart }
            DatePeriod.LAST_MONTH -> filtered.filter { it.timestamp in lastMonthStart until currentMonthStart }
            DatePeriod.LAST_90_DAYS -> filtered.filter { it.timestamp >= last90DaysStart }
            DatePeriod.ALL_TIME -> filtered
        }

        // Category filter
        if (!catFilter.isNullOrBlank()) {
            filtered = filtered.filter { it.categoryId == catFilter }
        }

        // Account filter
        if (!accFilter.isNullOrBlank()) {
            filtered = filtered.filter { it.accountId == accFilter }
        }

        // Direction filter
        if (dirFilter != null) {
            filtered = if (dirFilter == TransactionDirection.CREDIT) {
                filtered.filter { it.amount > 0 }
            } else {
                filtered.filter { it.amount < 0 }
            }
        }

        // Search Query filter
        if (search.isNotBlank()) {
            val q = search.trim().lowercase(Locale.ROOT)
            filtered = filtered.filter { tx ->
                tx.counterparty.lowercase(Locale.ROOT).contains(q) ||
                        (tx.notes != null && tx.notes.lowercase(Locale.ROOT).contains(q)) ||
                        (tx.externalRef != null && tx.externalRef.lowercase(Locale.ROOT).contains(q)) ||
                        (tx.categoryName != null && tx.categoryName.lowercase(Locale.ROOT).contains(q))
            }
        }

        // Sorting
        filtered = when (sort) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.timestamp }
            SortOrder.AMOUNT_DESC -> filtered.sortedByDescending { abs(it.amount) }
            SortOrder.AMOUNT_ASC -> filtered.sortedBy { abs(it.amount) }
        }

        // Category Spend Summary (for current selected period)
        val periodExpenses = filtered.filter { it.amount < 0 }
        val periodTotalExpense = abs(periodExpenses.sumOf { it.amount })

        val categorySpendList = categories.map { cat ->
            val catSpend = abs(periodExpenses.filter { it.categoryId == cat.id }.sumOf { it.amount })
            val pct = if (periodTotalExpense > 0) catSpend / periodTotalExpense else 0.0
            val budget = cat.budgetLimit
            val progress = if (budget != null && budget > 0) catSpend / budget else 0.0
            val isOver = budget != null && budget > 0 && catSpend > budget

            CategorySpendSummary(
                category = cat,
                totalSpend = catSpend,
                percentageOfTotal = pct,
                budgetLimit = budget,
                budgetProgress = progress,
                isOverBudget = isOver
            )
        }.filter { it.totalSpend > 0 || (it.budgetLimit != null && it.budgetLimit > 0) }
            .sortedByDescending { it.totalSpend }

        // Daily Cash Flow calculation (grouped by day for charts)
        val dayFormat = SimpleDateFormat("MMM dd", Locale.US)
        val dailyMap = mutableMapOf<String, Pair<Double, Double>>()
        val dailyTimestamps = mutableMapOf<String, Long>()

        val chartTransactions = if (period == DatePeriod.THIS_MONTH || period == DatePeriod.ALL_TIME) {
            transactions.filter { it.timestamp >= System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000) }
        } else {
            filtered
        }

        for (tx in chartTransactions.sortedBy { it.timestamp }) {
            val key = dayFormat.format(Date(tx.timestamp))
            val current = dailyMap.getOrDefault(key, 0.0 to 0.0)
            if (tx.amount > 0) {
                dailyMap[key] = (current.first + tx.amount) to current.second
            } else {
                dailyMap[key] = current.first to (current.second + abs(tx.amount))
            }
            dailyTimestamps[key] = tx.timestamp
        }

        val dailyCashFlow = dailyMap.map { (key, pair) ->
            DailyCashFlowBar(
                label = key,
                dateTimestamp = dailyTimestamps[key] ?: System.currentTimeMillis(),
                totalIncome = pair.first,
                totalExpense = pair.second
            )
        }

        // Top spending merchant
        val merchantGroup = periodExpenses.groupBy { it.counterparty }
        val topEntry = merchantGroup.maxByOrNull { entry -> entry.value.sumOf { abs(it.amount) } }
        val topMerchant = topEntry?.key ?: "None"
        val topSpend = topEntry?.value?.sumOf { abs(it.amount) } ?: 0.0

        // Avg daily expense (over active days in period)
        val activeDays = dailyMap.size.coerceAtLeast(1)
        val avgDaily = if (periodTotalExpense > 0) periodTotalExpense / activeDays else 0.0

        ExpenseUiState(
            allTransactions = transactions,
            filteredTransactions = filtered,
            accounts = accounts,
            categories = categories,
            duplicateCandidates = duplicates,
            unrecognizedMessages = unrecognized,
            totalMonthlySpent = thisMonthOutflow,
            totalTransactionsCount = transactions.size,
            thisMonthInflow = thisMonthInflow,
            thisMonthOutflow = thisMonthOutflow,
            thisMonthNet = thisMonthNet,
            lastMonthInflow = lastMonthInflow,
            lastMonthOutflow = lastMonthOutflow,
            inflowVariancePct = inflowVariancePct,
            outflowVariancePct = outflowVariancePct,
            totalMonthlyBudget = totalMonthlyBudget,
            monthlyBudgetUsedPct = budgetUsedPct,
            accountSpendMap = accountSpendMap,
            searchQuery = search,
            selectedPeriod = period,
            selectedCategoryFilter = catFilter,
            selectedAccountFilter = accFilter,
            selectedDirectionFilter = dirFilter,
            selectedSort = sort,
            categorySpendList = categorySpendList,
            dailyCashFlow = dailyCashFlow,
            topSpendingMerchant = topMerchant,
            topSpendAmount = topSpend,
            avgDailyExpense = avgDaily,
            preferredCurrency = "GHS",
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExpenseUiState(isLoading = true)
    )

    // --- Search & Filter Actions ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPeriod(period: DatePeriod) {
        _selectedPeriod.value = period
    }

    fun setCategoryFilter(categoryId: String?) {
        _selectedCategoryFilter.value = categoryId
    }

    fun setAccountFilter(accountId: String?) {
        _selectedAccountFilter.value = accountId
    }

    fun setDirectionFilter(direction: TransactionDirection?) {
        _selectedDirectionFilter.value = direction
    }

    fun setSortOrder(sort: SortOrder) {
        _selectedSort.value = sort
    }

    // --- Transaction CRUD Operations ---
    fun addTransaction(
        counterparty: String,
        amount: Double,
        currency: String = "GHS",
        isIncome: Boolean,
        accountId: String,
        categoryId: String,
        notes: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        sourceMethod: String = "manual",
        externalRef: String? = null,
        receiptImagePath: String? = null
    ) {
        viewModelScope.launch {
            val state = uiState.value
            val account = state.accounts.firstOrNull { it.id == accountId }
            val category = state.categories.firstOrNull { it.id == categoryId }

            val finalAmount = if (isIncome) abs(amount) else -abs(amount)
            val tx = TransactionEntity(
                externalRef = externalRef,
                accountId = accountId,
                categoryId = categoryId,
                amount = finalAmount,
                currency = currency,
                direction = if (isIncome) "CREDIT" else "DEBIT",
                type = if (isIncome) "Income" else "Expense",
                timestamp = timestamp,
                counterparty = counterparty,
                sourceMethod = sourceMethod,
                notes = notes,
                receiptImagePath = receiptImagePath,
                isVerified = true,
                categoryName = category?.name,
                categoryColor = category?.colorHex,
                accountRail = account?.name
            )

            val eval = reconciler.evaluate(tx, state.allTransactions)
            when (eval.outcome) {
                ReconciliationOutcome.IDEMPOTENT_SKIP -> {
                    _snackbarEvent.emit("Exact duplicate ignored: Ref ${tx.externalRef}")
                }
                ReconciliationOutcome.AUTO_MERGED -> {
                    eval.resolvedTransaction?.let { repository.updateTransaction(it) }
                    _snackbarEvent.emit("Auto-merged with existing record (${tx.counterparty})")
                }
                ReconciliationOutcome.QUEUED_FOR_REVIEW -> {
                    repository.addTransaction(tx)
                    _snackbarEvent.emit("Transaction recorded.")
                }
                ReconciliationOutcome.INSERTED_NEW -> {
                    repository.addTransaction(tx)
                    _snackbarEvent.emit("Transaction recorded successfully!")
                }
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _snackbarEvent.emit("Transaction deleted.")
        }
    }

    fun updateTransactionCategory(tx: TransactionEntity, category: CategoryEntity) {
        viewModelScope.launch {
            val updated = tx.copy(
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.colorHex
            )
            repository.updateTransaction(updated)
            _snackbarEvent.emit("Updated to ${category.name}")
        }
    }

    // --- Accounts & Categories Operations ---
    fun addAccount(name: String, type: String, balance: Double, currency: String) {
        viewModelScope.launch {
            val newAcc = AccountEntity(
                name = name,
                accountType = type,
                currentBalance = balance,
                currency = currency
            )
            repository.addAccount(newAcc)
            _snackbarEvent.emit("Payment method added: $name")
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            _snackbarEvent.emit("Account removed.")
        }
    }

    fun addCategory(name: String, icon: String, colorHex: String, budgetLimit: Double?) {
        viewModelScope.launch {
            val cat = CategoryEntity(
                name = name,
                icon = icon,
                colorHex = colorHex,
                budgetLimit = budgetLimit
            )
            repository.addCategory(cat)
            _snackbarEvent.emit("Category added: $name")
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
            _snackbarEvent.emit("Category updated: ${category.name}")
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            _snackbarEvent.emit("Category removed.")
        }
    }

    // --- Ingestion Pipeline Operations ---
    fun simulateSmsIngest(sender: String, body: String) {
        viewModelScope.launch {
            val (outcome, msg) = repository.ingestSms(sender, body)
            _snackbarEvent.emit(msg)
        }
    }

    fun parseReceiptText(text: String, onResult: (ReceiptDraft) -> Unit) {
        viewModelScope.launch {
            val draft = repository.parseReceipt(text)
            onResult(draft)
        }
    }

    // --- Duplicate Queue Actions ---
    fun mergeDuplicate(item: DuplicateCandidateItem) {
        viewModelScope.launch {
            repository.mergeDuplicate(item)
            _snackbarEvent.emit("Transactions merged and verified!")
        }
    }

    fun keepBothDuplicate(item: DuplicateCandidateItem) {
        viewModelScope.launch {
            repository.keepBothDuplicate(item)
            _snackbarEvent.emit("Both transactions kept in ledger.")
        }
    }

    fun dismissDuplicate(candidateId: String) {
        viewModelScope.launch {
            repository.dismissDuplicate(candidateId)
            _snackbarEvent.emit("Duplicate candidate dismissed.")
        }
    }

    // --- Unrecognized Messages Actions ---
    fun convertUnrecognizedToTransaction(
        unrecId: String,
        counterparty: String,
        amount: Double,
        currency: String,
        isIncome: Boolean,
        accountId: String,
        categoryId: String
    ) {
        viewModelScope.launch {
            val state = uiState.value
            val account = state.accounts.firstOrNull { it.id == accountId }
            val category = state.categories.firstOrNull { it.id == categoryId }

            val finalAmount = if (isIncome) abs(amount) else -abs(amount)
            val tx = TransactionEntity(
                accountId = accountId,
                categoryId = categoryId,
                amount = finalAmount,
                currency = currency,
                direction = if (isIncome) "CREDIT" else "DEBIT",
                type = if (isIncome) "Income" else "Expense",
                timestamp = System.currentTimeMillis(),
                counterparty = counterparty,
                sourceMethod = "sms",
                notes = "Converted from unrecognized alert",
                categoryName = category?.name,
                categoryColor = category?.colorHex,
                accountRail = account?.name
            )

            repository.resolveUnrecognized(unrecId, tx)
            _snackbarEvent.emit("Converted alert to transaction!")
        }
    }

    fun dismissUnrecognized(id: String) {
        viewModelScope.launch {
            repository.dismissUnrecognized(id)
            _snackbarEvent.emit("Alert dismissed")
        }
    }

    // --- CSV & Backup Export/Import ---
    fun exportCsv(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val csv = repository.exportCsv(uiState.value.filteredTransactions)
            onResult(csv)
        }
    }

    fun exportEncryptedBackup(passphrase: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val backup = repository.exportEncryptedBackup(passphrase)
            onResult(backup)
        }
    }

    fun importEncryptedBackup(payload: String, passphrase: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importEncryptedBackup(payload, passphrase)
            if (success) {
                _snackbarEvent.emit("Backup imported successfully!")
            } else {
                _snackbarEvent.emit("Failed to restore backup: Invalid passphrase or payload.")
            }
            onResult(success)
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetToDemoData()
            _snackbarEvent.emit("Demo dataset restored!")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllUserData()
            _snackbarEvent.emit("All transaction data cleared.")
        }
    }

    // --- Gemini AI Support & Personal Copilot ---
    private val geminiService = GeminiChatService()
    private val prefs = application.getSharedPreferences("folio_ai_prefs", Context.MODE_PRIVATE)

    private val _customGeminiApiKey = MutableStateFlow(prefs.getString("custom_gemini_api_key", "") ?: "")
    val customGeminiApiKey: StateFlow<String> = _customGeminiApiKey.asStateFlow()

    fun setCustomGeminiApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("custom_gemini_api_key", trimmed).apply()
        _customGeminiApiKey.value = trimmed
        viewModelScope.launch {
            _snackbarEvent.emit(if (trimmed.isNotBlank()) "Gemini API Key saved!" else "Gemini API Key cleared.")
        }
    }

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                text = "Hello! I'm your Folio AI Financial Copilot. I have real-time visibility into your accounts, recent transactions, and budgets. Ask me anything about your spending, balances, or financial health!"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessage(
                isUser = false,
                text = "Conversation reset. How can I help you analyze your accounts and spending today?"
            )
        )
    }

    fun sendAiMessage(promptText: String) {
        val prompt = promptText.trim()
        if (prompt.isBlank() || _isAiThinking.value) return

        val userMsg = ChatMessage(isUser = true, text = prompt)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiThinking.value = true

        viewModelScope.launch {
            val state = uiState.value

            // Build grounded financial context specific to this user
            val contextSb = StringBuilder()
            contextSb.append("You are 'Folio AI', an in-app personal financial support copilot and advisor for Folio Expenses.\n")
            contextSb.append("The user is asking a question. You have real-time access to their specific account data below.\n")
            contextSb.append("Answer questions specifically, concisely, accurately, and encouragement using ONLY the real numbers provided.\n\n")

            contextSb.append("--- USER'S SPECIFIC ACCOUNT & TRANSACTION DATA ---\n")
            contextSb.append("Preferred Currency: ${state.preferredCurrency}\n")
            contextSb.append("Total Outflow This Month: ${CurrencyUtils.format(state.thisMonthOutflow, state.preferredCurrency)}\n")
            contextSb.append("Total Inflow This Month: ${CurrencyUtils.format(state.thisMonthInflow, state.preferredCurrency)}\n")
            contextSb.append("Net Cash Flow This Month: ${CurrencyUtils.format(state.thisMonthNet, state.preferredCurrency)}\n")
            contextSb.append("Monthly Budget: ${CurrencyUtils.format(state.totalMonthlyBudget, state.preferredCurrency)} (${String.format(Locale.US, "%.0f", state.monthlyBudgetUsedPct * 100)}% used)\n\n")

            contextSb.append("User Accounts & Balances:\n")
            for (acc in state.accounts) {
                contextSb.append("- ${acc.name} (${acc.accountType}): ${acc.currency} ${String.format(Locale.US, "%.2f", acc.currentBalance)}\n")
            }

            contextSb.append("\nCategory Spending This Month:\n")
            for (cs in state.categorySpendList) {
                val budgetNote = cs.budgetLimit?.let { " [Budget: ${state.preferredCurrency} ${String.format(Locale.US, "%.2f", it)}]" } ?: ""
                contextSb.append("- ${cs.category.name}: ${CurrencyUtils.format(cs.totalSpend, state.preferredCurrency)} (${String.format(Locale.US, "%.1f", cs.percentageOfTotal)}%)$budgetNote\n")
            }

            contextSb.append("\nTop Spending Merchant: ${state.topSpendingMerchant} (${CurrencyUtils.format(state.topSpendAmount, state.preferredCurrency)})\n")
            contextSb.append("Average Daily Burn Rate: ${CurrencyUtils.format(state.avgDailyExpense, state.preferredCurrency)}\n")

            contextSb.append("\nRecent Transactions:\n")
            val recentTxs = state.allTransactions.take(15)
            val dFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            for (tx in recentTxs) {
                val flow = if (tx.amount >= 0) "INCOME" else "EXPENSE"
                val cat = tx.categoryName ?: "General"
                val rail = tx.accountRail ?: "Wallet"
                contextSb.append("- [${dFormat.format(Date(tx.timestamp))}] $flow ${tx.currency} ${String.format(Locale.US, "%.2f", abs(tx.amount))} at ${tx.counterparty} ($cat via $rail)\n")
            }

            if (state.duplicateCandidates.isNotEmpty() || state.unrecognizedMessages.isNotEmpty()) {
                contextSb.append("\nReview Queue: ${state.duplicateCandidates.size} duplicate candidates, ${state.unrecognizedMessages.size} unfiled SMS alerts.\n")
            }

            val fallback = buildString {
                append("• Net Flow: ${CurrencyUtils.format(state.thisMonthNet, state.preferredCurrency)}\n")
                append("• Total Spent This Month: ${CurrencyUtils.format(state.thisMonthOutflow, state.preferredCurrency)}\n")
                append("• Budget Used: ${String.format(Locale.US, "%.0f", state.monthlyBudgetUsedPct * 100)}%\n")
                append("• Accounts: ${state.accounts.joinToString { "${it.name} (${it.currency} ${String.format(Locale.US, "%.2f", it.currentBalance)})" }}\n")
                if (recentTxs.isNotEmpty()) {
                    append("• Latest: ${recentTxs.first().currency} ${String.format(Locale.US, "%.2f", abs(recentTxs.first().amount))} at ${recentTxs.first().counterparty}")
                }
            }

            val assistantResponse = geminiService.sendChat(
                history = _chatMessages.value,
                systemInstructionText = contextSb.toString(),
                fallbackDataSummary = fallback,
                apiKeyOverride = _customGeminiApiKey.value.ifBlank { null }
            )

            val botMsg = ChatMessage(isUser = false, text = assistantResponse)
            _chatMessages.value = _chatMessages.value + botMsg
            _isAiThinking.value = false
        }
    }
}
