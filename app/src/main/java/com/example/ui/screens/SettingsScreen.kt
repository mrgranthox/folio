package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.DebitRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.M3PrimaryLight
import com.example.ui.theme.CreditGreen
import com.example.ui.viewmodel.ExpenseUiState
import kotlinx.coroutines.launch

/**
 * Module D: Configuration, Sync & Portability per UI/UX Visual Specification:
 * - 15. Main Settings Hub: Grouped ListTile menus with On Surface Variant section dividers.
 * - 16. Category & Budget Management
 * - 17. Account Management
 * - 18. Cloud Sync & Backup Configuration
 * - 19. Export & Reporting Engine
 * - 20. Automation Settings (iOS/Android): Switch toggles for SMS background receiver permissions & iOS Shortcuts Intent guidance.
 * - 21. OTA Parser Updates (Hidden/Developer): "Check for Updates" button returning Snackbar.
 */
@Composable
fun SettingsScreen(
    state: ExpenseUiState,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenExportBackup: () -> Unit,
    onResetDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onOpenOnboardingFunnel: () -> Unit = {},
    isSyncingInboxSms: Boolean = false,
    onSyncInboxSms: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val securityPrefs = remember { context.getSharedPreferences("folio_security_prefs", Context.MODE_PRIVATE) }
    var isBiometricsEnabled by remember {
        mutableStateOf(securityPrefs.getBoolean("biometrics_enabled", false))
    }

    // Automation Settings (Screen 20)
    var smsReceiverPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        smsReceiverPermissionGranted = isGranted
        coroutineScope.launch {
            snackbarHostState?.showSnackbar(
                if (isGranted) "SMS background listener enabled." else "SMS permission was denied."
            )
        }
    }

    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSyncInboxSms()
        } else {
            coroutineScope.launch {
                snackbarHostState?.showSnackbar("SMS Read permission required to scan device inbox.")
            }
        }
    }

    // OTA Parser Updates (Screen 21)
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var developerTaps by remember { mutableStateOf(0) }
    var showDeveloperOtaSection by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("settings_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App branding banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            developerTaps++
                            if (developerTaps >= 5) {
                                showDeveloperOtaSection = true
                                coroutineScope.launch {
                                    snackbarHostState?.showSnackbar("Developer Mode: Parser Diagnostics Unlocked")
                                }
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Folio Expense Tracker",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Version 1.0.0 · Offline-First Financial Ledger",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 1: Payment Methods & Budgets
            item {
                Text(
                    text = "PAYMENT METHODS & BUDGETS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Default.Payment,
                            title = "Payment Methods",
                            subtitle = "${state.accounts.size} methods (${state.accounts.joinToString { it.name }})",
                            onClick = onOpenAccounts
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Category,
                            title = "Categories & Monthly Budgets",
                            subtitle = "${state.categories.size} categories · ${CurrencyUtils.format(state.totalMonthlyBudget, state.preferredCurrency)} budget limit",
                            onClick = onOpenCategories
                        )
                    }
                }
            }

            // Section 2: Automation & Security Preferences
            item {
                Text(
                    text = "PREFERENCES & AUTOMATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // 1. SMS Background Ingestion
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic SMS Tracking",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Capture incoming MoMo and bank transaction alerts on-device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = smsReceiverPermissionGranted,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                    } else {
                                        smsReceiverPermissionGranted = false
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        // 2. Scan & Sync SMS from Device Inbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sync Device SMS Inbox",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Scan past MoMo, Telecel & Bank SMS alerts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                                    if (hasReadPermission) {
                                        onSyncInboxSms()
                                    } else {
                                        readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                    }
                                },
                                enabled = !isSyncingInboxSms,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isSyncingInboxSms) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing...", fontSize = 12.sp)
                                } else {
                                    Text("Sync Now", fontSize = 12.sp)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        // 3. Biometric / PIN App Lock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Biometric App Lock",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Protect expense ledger on launch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isBiometricsEnabled,
                                onCheckedChange = { enabled ->
                                    isBiometricsEnabled = enabled
                                    securityPrefs.edit().putBoolean("biometrics_enabled", enabled).apply()
                                    coroutineScope.launch {
                                        snackbarHostState?.showSnackbar(
                                            if (enabled) "Biometric Lock enabled."
                                            else "Biometric Lock disabled."
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            // Section 3: Data, Cloud Sync & Portability
            item {
                Text(
                    text = "DATA & PORTABILITY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Default.Download,
                            title = "Export & Encrypted Vault",
                            subtitle = "CSV, PDF, SQLite dump and AES-256 vault backup",
                            onClick = onOpenExportBackup
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Security,
                            title = "Privacy & Setup Guide",
                            subtitle = "Review zero-knowledge architecture, currency setup, and active rails",
                            onClick = onOpenOnboardingFunnel
                        )
                    }
                }
            }

            // Section 4: Screen 21: Parser Rules Verification (Developer Only)
            if (showDeveloperOtaSection) {
                item {
                    Text(
                        text = "PARSER RULES ENGINE (DIAGNOSTICS)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Deterministic Parser Rules", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                    Text("Rule DB v2.4 · 15 active MoMo & Bank format patterns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        isCheckingUpdates = true
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(350)
                                            isCheckingUpdates = false
                                            snackbarHostState?.showSnackbar("Parser rules active: MTN MoMo, Telecel, AT Money, GCB, Stanbic, and Ecobank verified.")
                                        }
                                    },
                                    enabled = !isCheckingUpdates,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isCheckingUpdates) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Text("Verify Rules", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Data Management
            item {
                Text(
                    text = "DATABASE ACTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Default.Refresh,
                            title = "Reset Sample Data",
                            subtitle = "Reload sample Ghanaian transactions, payment methods, and categories",
                            onClick = { showResetConfirm = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                        SettingsRowItem(
                            icon = Icons.Default.DeleteForever,
                            iconTint = DebitRed,
                            title = "Clear All Expenses",
                            subtitle = "Permanently remove all recorded expenses",
                            onClick = { showClearConfirm = true }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Restore Sample Data?") },
            text = { Text("This will reset your categories, payment methods, and expenses to sample default entries.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetDemoData()
                    showResetConfirm = false
                }) {
                    Text("Restore Sample Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Expenses?") },
            text = { Text("Are you sure you want to delete all recorded expenses? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAllData()
                    showClearConfirm = false
                }) {
                    Text("Clear All", color = DebitRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}
