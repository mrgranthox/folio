package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransactionEntity
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest

enum class AppTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    TRANSACTIONS("Ledger", Icons.AutoMirrored.Filled.ReceiptLong),
    ANALYTICS("Analytics", Icons.Default.Analytics),
    REVIEW("Review", Icons.Default.NotificationsActive),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Sheet states
    var showAddExpenseSheet by remember { mutableStateOf(false) }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    var showOcrScannerSheet by remember { mutableStateOf(false) }
    var showQuickPasteSmsSheet by remember { mutableStateOf(false) }
    var showAccountsDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var showExportBackupDialog by remember { mutableStateOf(false) }

    val addExpenseSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ocrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickPasteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accountsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categoriesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val exportBackupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val reviewBadgeCount = state.duplicateCandidates.size + state.unrecognizedMessages.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Folio Expenses"
                            1 -> "Expense Ledger"
                            2 -> "Spending Analytics"
                            3 -> "Review & Clean Up"
                            4 -> "Settings"
                            else -> "Folio"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            if (tab == AppTab.REVIEW && reviewBadgeCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("$reviewBadgeCount")
                                    }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label, fontSize = 11.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab in 0..1) {
                FloatingActionButton(
                    onClick = { showAddExpenseSheet = true },
                    containerColor = PrimaryGreen,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_add_entry")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> OverviewScreen(
                    state = state,
                    onNavigateToTransactions = { selectedTab = 1 },
                    onNavigateToReviewQueue = { selectedTab = 3 },
                    onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                    onAddExpenseClick = { showAddExpenseSheet = true },
                    onScanReceiptClick = { showOcrScannerSheet = true },
                    onQuickPasteSms = { showQuickPasteSmsSheet = true }
                )
                1 -> TransactionsScreen(
                    state = state,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onPeriodChange = { viewModel.setPeriod(it) },
                    onCategoryFilterChange = { viewModel.setCategoryFilter(it) },
                    onAccountFilterChange = { viewModel.setAccountFilter(it) },
                    onDirectionFilterChange = { viewModel.setDirectionFilter(it) },
                    onSortChange = { viewModel.setSortOrder(it) },
                    onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                )
                2 -> AnalyticsScreen(
                    state = state,
                    onPeriodChange = { viewModel.setPeriod(it) }
                )
                3 -> ReviewQueueScreen(
                    state = state,
                    onMergeDuplicate = { viewModel.mergeDuplicate(it) },
                    onKeepBothDuplicate = { viewModel.keepBothDuplicate(it) },
                    onDismissDuplicate = { viewModel.dismissDuplicate(it) },
                    onConvertUnrecognized = { msg ->
                        val amt = msg.suspectedAmount?.toDoubleOrNull() ?: 0.0
                        val momoAcc = state.accounts.firstOrNull { it.accountType.equals("MOMO", ignoreCase = true) }?.id ?: state.accounts.firstOrNull()?.id ?: "acc-momo"
                        val defaultCat = state.categories.firstOrNull { it.name.contains("Food", ignoreCase = true) }?.id ?: state.categories.firstOrNull()?.id ?: "cat-food"
                        viewModel.convertUnrecognizedToTransaction(
                            unrecId = msg.id,
                            counterparty = msg.suspectedMerchant ?: "SMS Merchant",
                            amount = amt,
                            currency = "GHS",
                            isIncome = false,
                            accountId = momoAcc,
                            categoryId = defaultCat
                        )
                    },
                    onDismissUnrecognized = { viewModel.dismissUnrecognized(it) }
                )
                4 -> SettingsScreen(
                    state = state,
                    onOpenAccounts = { showAccountsDialog = true },
                    onOpenCategories = { showCategoriesDialog = true },
                    onOpenExportBackup = { showExportBackupDialog = true },
                    onResetDemoData = { viewModel.resetDemoData() },
                    onClearAllData = { viewModel.clearAllData() }
                )
            }
        }
    }

    // Modal Bottom Sheets
    if (showAddExpenseSheet) {
        AddExpenseSheet(
            accounts = state.accounts,
            categories = state.categories,
            sheetState = addExpenseSheetState,
            onDismiss = { showAddExpenseSheet = false },
            onSave = { payee, amt, curr, isInc, accId, catId, notes ->
                viewModel.addTransaction(
                    counterparty = payee,
                    amount = amt,
                    currency = curr,
                    isIncome = isInc,
                    accountId = accId,
                    categoryId = catId,
                    notes = notes
                )
            },
            onScanReceiptClicked = { showOcrScannerSheet = true }
        )
    }

    selectedTransactionForDetail?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            categories = state.categories,
            sheetState = detailSheetState,
            onDismiss = { selectedTransactionForDetail = null },
            onUpdateCategory = { item, newCat ->
                viewModel.updateTransactionCategory(item, newCat)
            },
            onDelete = { id ->
                viewModel.deleteTransaction(id)
                selectedTransactionForDetail = null
            }
        )
    }

    if (showOcrScannerSheet) {
        OcrScannerSheet(
            accounts = state.accounts,
            categories = state.categories,
            sheetState = ocrSheetState,
            onDismiss = { showOcrScannerSheet = false },
            onParseText = { text, callback ->
                viewModel.parseReceiptText(text, callback)
            },
            onSaveTransaction = { payee, amt, curr, isInc, accId, catId, notes, ts, img ->
                viewModel.addTransaction(
                    counterparty = payee,
                    amount = amt,
                    currency = curr,
                    isIncome = isInc,
                    accountId = accId,
                    categoryId = catId,
                    notes = notes,
                    timestamp = ts,
                    receiptImagePath = img
                )
            }
        )
    }

    if (showQuickPasteSmsSheet) {
        QuickPasteSmsDialog(
            sheetState = quickPasteSheetState,
            onDismiss = { showQuickPasteSmsSheet = false },
            onIngest = { rawSms ->
                viewModel.simulateSmsIngest("MobileMoney", rawSms)
            }
        )
    }

    if (showAccountsDialog) {
        AccountsManagementDialog(
            accounts = state.accounts,
            sheetState = accountsSheetState,
            onDismiss = { showAccountsDialog = false },
            onAddAccount = { name, type, bal, curr ->
                viewModel.addAccount(name, type, bal, curr)
            },
            onDeleteAccount = { id ->
                viewModel.deleteAccount(id)
            }
        )
    }

    if (showCategoriesDialog) {
        CategoriesBudgetsDialog(
            categories = state.categories,
            sheetState = categoriesSheetState,
            onDismiss = { showCategoriesDialog = false },
            onAddCategory = { name, icon, color, budget ->
                viewModel.addCategory(name, icon, color, budget)
            },
            onUpdateCategory = { cat ->
                viewModel.updateCategory(cat)
            },
            onDeleteCategory = { id ->
                viewModel.deleteCategory(id)
            }
        )
    }

    if (showExportBackupDialog) {
        ExportBackupDialog(
            sheetState = exportBackupSheetState,
            onDismiss = { showExportBackupDialog = false },
            onExportCsv = { callback ->
                viewModel.exportCsv(callback)
            },
            onExportEncrypted = { pass, callback ->
                viewModel.exportEncryptedBackup(pass, callback)
            },
            onImportEncrypted = { payload, pass, callback ->
                viewModel.importEncryptedBackup(payload, pass, callback)
            }
        )
    }
}
