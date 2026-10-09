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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import android.app.Activity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.data.security.FolioSecurityManager
import com.example.data.security.SecurityLockType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.M3PrimaryLight
import com.example.ui.theme.PrimaryGreen
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
    onExportBackup: () -> Unit = {},
    onResetDemoData: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    onOpenPrivacyTerms: () -> Unit = {},
    isSyncingInboxSms: Boolean = false,
    onSyncInboxSms: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val securityPrefs = remember { context.getSharedPreferences("folio_security_prefs", Context.MODE_PRIVATE) }
    var isBiometricsEnabled by remember {
        mutableStateOf(FolioSecurityManager.isAppLockEnabled(context))
    }
    var lockType by remember {
        mutableStateOf(FolioSecurityManager.getLockType(context))
    }
    var hasCustomPin by remember {
        mutableStateOf(FolioSecurityManager.hasCustomPin(context))
    }
    var showLockSetupSheet by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }

    val deviceAuthVerificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        FolioSecurityManager.setExternalIntentActive(false)
        if (result.resultCode == Activity.RESULT_OK) {
            FolioSecurityManager.setLockType(context, SecurityLockType.SYSTEM)
            FolioSecurityManager.setAppLockEnabled(context, true)
            isBiometricsEnabled = true
            lockType = SecurityLockType.SYSTEM
            coroutineScope.launch {
                snackbarHostState?.showSnackbar("Device Screen Lock enabled successfully.")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState?.showSnackbar("Device authentication cancelled.")
            }
        }
    }

    var isAutomaticSmsTrackingEnabled by remember {
        mutableStateOf(securityPrefs.getBoolean("automatic_sms_tracking", true))
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
        FolioSecurityManager.setExternalIntentActive(false)
        smsReceiverPermissionGranted = isGranted
        if (isGranted) {
            isAutomaticSmsTrackingEnabled = true
            securityPrefs.edit().putBoolean("automatic_sms_tracking", true).apply()
        }
        coroutineScope.launch {
            snackbarHostState?.showSnackbar(
                if (isGranted) "SMS background listener enabled." else "SMS permission was denied."
            )
        }
    }

    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        FolioSecurityManager.setExternalIntentActive(false)
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
            // Scrollable Page Header (Settings)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Preferences & Data Automation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onSyncInboxSms,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

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
                        // 1. Automatic SMS Tracking
                        val isSmsActive = isAutomaticSmsTrackingEnabled
                        val handleToggleSms: (Boolean) -> Unit = { enable ->
                            isAutomaticSmsTrackingEnabled = enable
                            securityPrefs.edit().putBoolean("automatic_sms_tracking", enable).apply()
                            if (enable) {
                                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                                if (!hasPerm) {
                                    FolioSecurityManager.setExternalIntentActive(true)
                                    smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                } else {
                                    smsReceiverPermissionGranted = true
                                    coroutineScope.launch { snackbarHostState?.showSnackbar("Automatic SMS tracking enabled.") }
                                }
                            } else {
                                coroutineScope.launch { snackbarHostState?.showSnackbar("Automatic SMS tracking disabled.") }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSmsActive) PrimaryGreen.copy(alpha = 0.08f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { handleToggleSms(!isSmsActive) }
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSmsActive) PrimaryGreen.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (isSmsActive) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Automatic SMS Tracking",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSmsActive) CreditGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isSmsActive) "ACTIVE" else "OFF",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.sp
                                                ),
                                                color = if (isSmsActive) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isSmsActive) "Actively capturing MoMo & bank alerts" else "Tap to turn on automatic alert detection",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSmsActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    checked = isSmsActive,
                                    onCheckedChange = handleToggleSms,
                                    thumbContent = {
                                        Icon(
                                            imageVector = if (isSmsActive) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PrimaryGreen,
                                        checkedIconColor = PrimaryGreen,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                        uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
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
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sync Device SMS Inbox",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Scan past MoMo, Telecel & Bank SMS alerts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                                    if (hasReadPermission) {
                                        onSyncInboxSms()
                                    } else {
                                        FolioSecurityManager.setExternalIntentActive(true)
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
                                    Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        // 3. Biometric / PIN App Lock
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBiometricsEnabled) PrimaryGreen.copy(alpha = 0.08f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isBiometricsEnabled) {
                                                showDisableConfirmDialog = true
                                            } else {
                                                showLockSetupSheet = true
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isBiometricsEnabled) PrimaryGreen.copy(alpha = 0.18f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (lockType == SecurityLockType.CUSTOM_PIN) Icons.Default.Lock else Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            tint = if (isBiometricsEnabled) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Biometric & App Lock",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isBiometricsEnabled) CreditGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (isBiometricsEnabled) "ACTIVE" else "OFF",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 9.sp
                                                    ),
                                                    color = if (isBiometricsEnabled) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isBiometricsEnabled) {
                                                if (lockType == SecurityLockType.CUSTOM_PIN) "Protected by Custom Folio 4-Digit PIN"
                                                else "Protected by Device Screen Lock (Fingerprint / PIN)"
                                            } else {
                                                "Secure ledger with device settings or custom PIN"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isBiometricsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Switch(
                                        checked = isBiometricsEnabled,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                showLockSetupSheet = true
                                            } else {
                                                showDisableConfirmDialog = true
                                            }
                                        },
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (isBiometricsEnabled) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = PrimaryGreen,
                                            checkedIconColor = PrimaryGreen,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                            uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                if (isBiometricsEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showLockSetupSheet = true },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Change Method", fontSize = 12.sp)
                                        }

                                        if (lockType == SecurityLockType.CUSTOM_PIN) {
                                            Button(
                                                onClick = { showPinSetupDialog = true },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Change PIN", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Privacy & Legal
            item {
                Text(
                    text = "PRIVACY & LEGAL",
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
                            icon = Icons.Default.Security,
                            title = "Privacy Policy & Terms of Service",
                            subtitle = "Zero-knowledge local storage, data protection, and usage terms",
                            onClick = onOpenPrivacyTerms
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        if (showLockSetupSheet) {
            AppLockSetupSheet(
                onDismiss = { showLockSetupSheet = false },
                onSelectSystemLock = {
                    showLockSetupSheet = false
                    val intent = FolioSecurityManager.createConfirmDeviceCredentialIntent(context)
                    if (intent != null) {
                        FolioSecurityManager.setExternalIntentActive(true)
                        deviceAuthVerificationLauncher.launch(intent)
                    } else {
                        FolioSecurityManager.setLockType(context, SecurityLockType.SYSTEM)
                        FolioSecurityManager.setAppLockEnabled(context, true)
                        isBiometricsEnabled = true
                        lockType = SecurityLockType.SYSTEM
                        coroutineScope.launch {
                            snackbarHostState?.showSnackbar("Device Screen Lock enabled.")
                        }
                    }
                },
                onSelectCustomPin = {
                    showLockSetupSheet = false
                    showPinSetupDialog = true
                }
            )
        }

        if (showPinSetupDialog) {
            PinSetupDialog(
                onDismiss = { showPinSetupDialog = false },
                onPinCreated = { newPin ->
                    showPinSetupDialog = false
                    FolioSecurityManager.setCustomPin(context, newPin)
                    FolioSecurityManager.setLockType(context, SecurityLockType.CUSTOM_PIN)
                    FolioSecurityManager.setAppLockEnabled(context, true)
                    isBiometricsEnabled = true
                    lockType = SecurityLockType.CUSTOM_PIN
                    hasCustomPin = true
                    coroutineScope.launch {
                        snackbarHostState?.showSnackbar("Custom Folio PIN set and App Lock enabled.")
                    }
                }
            )
        }

        if (showDisableConfirmDialog) {
            DisableAppLockDialog(
                onDismiss = { showDisableConfirmDialog = false },
                onConfirmDisable = {
                    showDisableConfirmDialog = false
                    FolioSecurityManager.setAppLockEnabled(context, false)
                    isBiometricsEnabled = false
                    coroutineScope.launch {
                        snackbarHostState?.showSnackbar("App Lock disabled.")
                    }
                }
            )
        }
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
