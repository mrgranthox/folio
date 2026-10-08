package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.ParsedSmsResult
import com.example.data.engine.SmsParserEngine
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionDirection
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningOrange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPasteSmsDialog(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onIngest: (String) -> Unit,
    onIngestWithDetails: ((rawSms: String, accountId: String?, categoryId: String?) -> Unit)? = null,
    accounts: List<AccountEntity> = emptyList(),
    categories: List<CategoryEntity> = emptyList()
) {
    var smsText by remember { mutableStateOf("") }
    var isConfirmedAndSaved by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val parserEngine = remember { SmsParserEngine() }

    // Live parsed result as soon as SMS text is entered
    val parsedResult: ParsedSmsResult? = remember(smsText) {
        if (smsText.isNotBlank()) {
            parserEngine.parse("MobileMoney", smsText)
        } else {
            null
        }
    }

    // Auto-match category
    val autoMatchedCategory = remember(parsedResult, categories) {
        if (parsedResult == null) null
        else {
            val lower = "${parsedResult.counterparty.lowercase(Locale.ROOT)} ${parsedResult.rawBody.lowercase(Locale.ROOT)}"
            categories.firstOrNull { cat ->
                val cName = cat.name.lowercase(Locale.ROOT)
                if (lower.contains("food") || lower.contains("restaurant") || lower.contains("chop") || lower.contains("inn") || lower.contains("kfc") || lower.contains("buka") || lower.contains("lunch") || lower.contains("pizza")) {
                    cName.contains("food") || cName.contains("dining")
                } else if (lower.contains("bolt") || lower.contains("uber") || lower.contains("fuel") || lower.contains("shell") || lower.contains("total") || lower.contains("goil") || lower.contains("ride")) {
                    cName.contains("transport") || cName.contains("fuel")
                } else if (lower.contains("ecg") || lower.contains("water") || lower.contains("dstv") || lower.contains("power") || lower.contains("airtime") || lower.contains("bundle")) {
                    cName.contains("bills") || cName.contains("utilities")
                } else if (lower.contains("mart") || lower.contains("grocer") || lower.contains("shop") || lower.contains("supermarket")) {
                    cName.contains("shopping") || cName.contains("groceries")
                } else false
            } ?: categories.firstOrNull()
        }
    }

    // Auto-match account
    val autoMatchedAccount = remember(parsedResult, accounts) {
        if (parsedResult == null) null
        else {
            val provider = parsedResult.provider.lowercase(Locale.ROOT)
            accounts.firstOrNull { acc ->
                val accName = acc.name.lowercase(Locale.ROOT)
                val accType = acc.accountType.lowercase(Locale.ROOT)
                if (provider.contains("momo") || provider.contains("mtn")) {
                    accType.contains("momo") || accName.contains("mtn") || accName.contains("momo")
                } else if (provider.contains("telecel") || provider.contains("vodafone")) {
                    accName.contains("telecel") || accName.contains("vodafone")
                } else if (provider.contains("bank")) {
                    accType.contains("bank") || accName.contains("bank")
                } else false
            } ?: accounts.firstOrNull()
        }
    }

    var userSelectedAccountId by remember(autoMatchedAccount) {
        mutableStateOf(autoMatchedAccount?.id)
    }
    var userSelectedCategoryId by remember(autoMatchedCategory) {
        mutableStateOf(autoMatchedCategory?.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
                .testTag("quick_paste_sms_sheet")
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    text = if (isConfirmedAndSaved) "Confirmation" else "Paste Payment SMS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isConfirmedAndSaved) Icons.Default.CheckCircle else Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = if (isConfirmedAndSaved) CreditGreen else PrimaryGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isConfirmedAndSaved && parsedResult != null) {
                // ==========================================
                // VIEW 2: POST-INGESTION CONFIRMATION SCREEN
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(CreditGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = CreditGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Expense Successfully Recorded!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The payment alert has been parsed, reconciled, and added to your ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Transaction Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "AMOUNT RECORDED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${parsedResult.currency} ${String.format("%.2f", parsedResult.amount ?: 0.0)}",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (parsedResult.direction == TransactionDirection.DEBIT) DebitRed else CreditGreen
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (parsedResult.direction == TransactionDirection.DEBIT) "EXPENSE" else "INCOME",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Detail Rows
                            DetailItemRow(
                                label = "Merchant / Payee",
                                value = parsedResult.counterparty,
                                icon = Icons.Default.Storefront
                            )
                            DetailItemRow(
                                label = "Payment Rail",
                                value = autoMatchedAccount?.name ?: parsedResult.provider,
                                icon = Icons.Default.CreditCard
                            )
                            autoMatchedCategory?.let { cat ->
                                DetailItemRow(
                                    label = "Category",
                                    value = cat.name,
                                    icon = Icons.Default.Category
                                )
                            }
                            parsedResult.externalRef?.let { ref ->
                                DetailItemRow(
                                    label = "Transaction Ref",
                                    value = ref,
                                    icon = Icons.Default.Numbers
                                )
                            }
                            parsedResult.endingBalance?.let { bal ->
                                DetailItemRow(
                                    label = "Ending Balance",
                                    value = "${parsedResult.currency} ${String.format("%.2f", bal)}",
                                    icon = Icons.Default.AccountBalanceWallet
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                smsText = ""
                                isConfirmedAndSaved = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Another")
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // ==========================================
                // VIEW 1: PASTE INPUT & LIVE CONFIRMATION
                // ==========================================
                Text(
                    text = "Paste any payment alert SMS from MTN MoMo, Telecel Cash, AT Money, or bank alerts. Details will be extracted instantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Paste Buttons & Template Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick templates:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                smsText = clipText
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Clipboard", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SMS Input Field
                OutlinedTextField(
                    value = smsText,
                    onValueChange = { smsText = it },
                    label = { Text("SMS Message Text") },
                    placeholder = { Text("Paste message here (e.g. Payment made for GHS 45.00 to CHICKEN INN...)") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        if (smsText.isNotBlank()) {
                            IconButton(onClick = { smsText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // LIVE PARSED DETAILS CONFIRMATION CARD
                AnimatedVisibility(
                    visible = parsedResult != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (parsedResult != null) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Text(
                                text = "EXTRACTED DETAILS CONFIRMATION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.1.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Status Badge Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                parsedResult.isFinancial -> CreditGreen.copy(alpha = 0.15f)
                                                parsedResult.isPromotional -> DebitRed.copy(alpha = 0.15f)
                                                else -> WarningAmber.copy(alpha = 0.15f)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (parsedResult.isFinancial) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = when {
                                                        parsedResult.isFinancial -> CreditGreen
                                                        parsedResult.isPromotional -> DebitRed
                                                        else -> WarningAmber
                                                    },
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = when {
                                                        parsedResult.isFinancial -> "Verified Financial Alert"
                                                        parsedResult.isPromotional -> "Promotional / Marketing Message"
                                                        else -> "Unverified Alert Format"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = when {
                                                        parsedResult.isFinancial -> CreditGreen
                                                        parsedResult.isPromotional -> DebitRed
                                                        else -> WarningAmber
                                                    }
                                                )
                                            }
                                        }

                                        Text(
                                            text = parsedResult.provider,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Main Hero Amount Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Parsed Amount",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val amt = parsedResult.amount ?: 0.0
                                            Text(
                                                text = "${parsedResult.currency} ${String.format("%.2f", amt)}",
                                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (parsedResult.direction == TransactionDirection.DEBIT) DebitRed else CreditGreen
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (parsedResult.direction == TransactionDirection.DEBIT) DebitRed.copy(alpha = 0.12f) else CreditGreen.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = if (parsedResult.direction == TransactionDirection.DEBIT) "OUTFLOW (EXPENSE)" else "INFLOW (INCOME)",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (parsedResult.direction == TransactionDirection.DEBIT) DebitRed else CreditGreen
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Verified Fields Breakdown
                                    DetailItemRow(
                                        label = "Detected Payee / Merchant",
                                        value = parsedResult.counterparty,
                                        icon = Icons.Default.Storefront
                                    )
                                    parsedResult.rechargeToken?.let { token ->
                                        DetailItemRow(
                                            label = "Prepaid Recharge Token",
                                            value = token,
                                            icon = Icons.Default.DoneAll
                                        )
                                    }
                                    parsedResult.meterNumber?.let { meter ->
                                        DetailItemRow(
                                            label = "Meter Number",
                                            value = meter,
                                            icon = Icons.Default.Numbers
                                        )
                                    }
                                    val chosenAccName = accounts.firstOrNull { it.id == userSelectedAccountId }?.name ?: autoMatchedAccount?.name ?: parsedResult.provider
                                    DetailItemRow(
                                        label = "Assigned Wallet Rail",
                                        value = chosenAccName,
                                        icon = Icons.Default.CreditCard
                                    )
                                    val chosenCatName = categories.firstOrNull { it.id == userSelectedCategoryId }?.name ?: autoMatchedCategory?.name ?: "General"
                                    DetailItemRow(
                                        label = "Assigned Category",
                                        value = chosenCatName,
                                        icon = Icons.Default.Category
                                    )
                                    if (categories.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(categories) { cat ->
                                                val isSelected = cat.id == userSelectedCategoryId
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { userSelectedCategoryId = cat.id },
                                                    label = { Text(cat.name, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                    parsedResult.fee?.let { fee ->
                                        DetailItemRow(
                                            label = "Transaction Fee / Levy",
                                            value = "${parsedResult.currency} ${String.format(Locale.US, "%.2f", fee)}",
                                            icon = Icons.Default.Check
                                        )
                                    }
                                    parsedResult.externalRef?.let { ref ->
                                        DetailItemRow(
                                            label = "Financial Reference ID",
                                            value = ref,
                                            icon = Icons.Default.Numbers
                                        )
                                    }
                                    parsedResult.endingBalance?.let { bal ->
                                        DetailItemRow(
                                            label = "Account Balance in SMS",
                                            value = "${parsedResult.currency} ${String.format("%.2f", bal)}",
                                            icon = Icons.Default.AccountBalanceWallet
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (parsedResult != null && !parsedResult.isFinancial) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parsedResult.rejectionReason ?: "This message is promotional or cannot be verified as an executed financial transaction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Action Button: Confirm & Save
                Button(
                    onClick = {
                        if (smsText.isNotBlank() && parsedResult?.isFinancial == true) {
                            if (onIngestWithDetails != null) {
                                onIngestWithDetails(smsText, userSelectedAccountId, userSelectedCategoryId)
                            } else {
                                onIngest(smsText)
                            }
                            isConfirmedAndSaved = true
                        }
                    },
                    enabled = smsText.isNotBlank() && (parsedResult?.isFinancial == true),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (parsedResult?.isFinancial == false) "Non-Transactional Message" else "Confirm & Save Expense",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
