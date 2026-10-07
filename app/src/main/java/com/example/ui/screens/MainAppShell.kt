package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransactionEntity
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

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
    val context = LocalContext.current
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
    var showOnboardingFunnel by remember { mutableStateOf(false) }
    var showAiChatSheet by remember { mutableStateOf(false) }

    val addExpenseSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ocrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickPasteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accountsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categoriesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val exportBackupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiChatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val customGeminiApiKey by viewModel.customGeminiApiKey.collectAsStateWithLifecycle()

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
                            3 -> "Review"
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
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
                    onClearAllData = { viewModel.clearAllData() },
                    onOpenOnboardingFunnel = { showOnboardingFunnel = true },
                    isSyncingInboxSms = viewModel.isSyncingInboxSms.value,
                    onSyncInboxSms = { viewModel.syncInboxSms(context) },
                    snackbarHostState = snackbarHostState
                )
            }

            // Floating Action Buttons (Gemini AI on top, Add Expense + below)
            if (selectedTab in 0..1) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 78.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Gemini AI Button (logo only, on top of add expense)
                    FloatingActionButton(
                        onClick = { showAiChatSheet = true },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("gemini_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Add Expense Button (just the + sign, no text)
                    FloatingActionButton(
                        onClick = { showAddExpenseSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("fab_add_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Expense",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Floating Navigation Tab Bar (floats over content at BottomCenter, 3dp above navigation bar)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTab.entries.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            val animatedBgColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                label = "tab_bg"
                            )
                            val animatedContentColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "tab_content"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(animatedBgColor)
                                    .clickable { selectedTab = index }
                                    .testTag("tab_${tab.name.lowercase(Locale.ROOT)}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    if (tab == AppTab.REVIEW && reviewBadgeCount > 0) {
                                        BadgedBox(badge = {
                                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                Text("$reviewBadgeCount")
                                            }
                                        }) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label,
                                                tint = animatedContentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            tint = animatedContentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = animatedContentColor
                                    )
                                }
                            }
                        }
                    }
                }
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
            onAnalyzeImage = { ctx, uri, callback ->
                viewModel.analyzeReceiptImage(ctx, uri, callback)
            },
            onParseText = { text, callback ->
                viewModel.parseReceiptText(text, callback)
            },
            onSaveTransaction = { payee, amt, curr, isInc, accId, catId, notes, ts, img, ref ->
                viewModel.addTransaction(
                    counterparty = payee,
                    amount = amt,
                    currency = curr,
                    isIncome = isInc,
                    accountId = accId,
                    categoryId = catId,
                    notes = notes,
                    timestamp = ts,
                    sourceMethod = "ocr",
                    externalRef = ref,
                    receiptImagePath = img
                )
            }
        )
    }

    if (showQuickPasteSmsSheet) {
        QuickPasteSmsDialog(
            accounts = state.accounts,
            categories = state.categories,
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

    if (showOnboardingFunnel) {
        OnboardingFunnelScreen(
            onComplete = { selectedCurrency, selectedRails ->
                showOnboardingFunnel = false
            },
            onDismiss = { showOnboardingFunnel = false }
        )
    }

    if (showAiChatSheet) {
        FolioAiChatSheet(
            messages = chatMessages,
            isThinking = isAiThinking,
            onSendMessage = { prompt -> viewModel.sendAiMessage(prompt) },
            onClearHistory = { viewModel.clearChatHistory() },
            apiKey = customGeminiApiKey,
            onSaveApiKey = { key -> viewModel.setCustomGeminiApiKey(key) },
            sheetState = aiChatSheetState,
            onDismiss = { showAiChatSheet = false }
        )
    }
}
