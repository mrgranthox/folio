package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.data.security.FolioSecurityManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.engine.ReceiptDraft
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.ui.components.CurrencyUtils
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.WarningAmber
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onAnalyzeImage: ((Context, Uri, (ReceiptDraft?) -> Unit) -> Unit)? = null,
    onParseText: (String, (ReceiptDraft) -> Unit) -> Unit,
    onSaveTransaction: (
        merchant: String,
        amount: Double,
        currency: String,
        isIncome: Boolean,
        accountId: String,
        categoryId: String,
        notes: String?,
        timestamp: Long,
        receiptImagePath: String?,
        externalRef: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val photoUri = photoUriString?.let { Uri.parse(it) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var isProcessingOcr by rememberSaveable { mutableStateOf(false) }
    var ocrStatusText by rememberSaveable { mutableStateOf("Processing...") }

    var merchant by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("GHS") }
    var referenceNumber by rememberSaveable { mutableStateOf("") }
    var transactionType by rememberSaveable { mutableStateOf("EXPENSE") } // "EXPENSE", "INCOME", "BILL_PAYMENT", "TRANSFER"
    var taxVat by rememberSaveable { mutableDoubleStateOf(0.0) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confidenceScore by rememberSaveable { mutableStateOf<Int?>(null) }
    var isAiVerified by rememberSaveable { mutableStateOf(false) }
    var selectedAccountId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedCategoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var receiptTimestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    fun applyDraft(draft: ReceiptDraft, fromAi: Boolean) {
        merchant = draft.merchant ?: ""
        val amt = draft.amount ?: 0.0
        amountText = if (amt > 0) String.format(Locale.US, "%.2f", amt) else ""
        currency = draft.currency
        referenceNumber = draft.referenceNumber ?: ""
        transactionType = draft.transactionType
        taxVat = draft.tax ?: 0.0
        confidenceScore = (draft.confidenceScore * 100).toInt()
        receiptTimestamp = draft.date
        isAiVerified = fromAi

        // Notes and line items
        val refNote = if (!draft.referenceNumber.isNullOrBlank()) " [Ref: ${draft.referenceNumber}]" else ""
        val taxNote = if (draft.tax != null && draft.tax > 0) " (Includes Tax: ${CurrencyUtils.format(draft.tax, draft.currency)})" else ""
        val summaryNote = if (!draft.itemsSummary.isNullOrBlank()) draft.itemsSummary else ""
        notes = listOfNotNull(
            summaryNote.ifBlank { null },
            if (fromAi) "Scanned with Gemini Vision Intelligence$refNote$taxNote" else "Scanned via On-Device OCR$refNote$taxNote"
        ).joinToString(" • ")

        // Category Auto-matching
        val suggested = draft.suggestedCategory ?: ""
        val matchedCat = categories.firstOrNull { cat ->
            cat.name.equals(suggested, ignoreCase = true) ||
                    (suggested.isNotBlank() && cat.name.contains(suggested.split(" ").first(), ignoreCase = true)) ||
                    (merchant.isNotBlank() && cat.name.contains("Food", ignoreCase = true) && (merchant.contains("KFC", ignoreCase = true) || merchant.contains("Chop", ignoreCase = true) || merchant.contains("Inn", ignoreCase = true) || merchant.contains("Pizza", ignoreCase = true))) ||
                    (merchant.isNotBlank() && cat.name.contains("Bills", ignoreCase = true) && (merchant.contains("ECG", ignoreCase = true) || merchant.contains("Meter", ignoreCase = true) || merchant.contains("Water", ignoreCase = true))) ||
                    (merchant.isNotBlank() && cat.name.contains("Transport", ignoreCase = true) && (merchant.contains("Shell", ignoreCase = true) || merchant.contains("Total", ignoreCase = true) || merchant.contains("Goil", ignoreCase = true) || merchant.contains("Fuel", ignoreCase = true)))
        }
        if (matchedCat != null) {
            selectedCategoryId = matchedCat.id
        }

        // Account / Wallet Auto-matching
        val rail = draft.paymentRail ?: ""
        val matchedAccount = accounts.firstOrNull { acc ->
            (rail.contains("momo", ignoreCase = true) || rail.contains("mtn", ignoreCase = true)) && acc.accountType.equals("MOBILE_MONEY", ignoreCase = true) && acc.name.contains("MTN", ignoreCase = true) ||
                    (rail.contains("telecel", ignoreCase = true) || rail.contains("vodafone", ignoreCase = true)) && acc.name.contains("Telecel", ignoreCase = true) ||
                    (rail.contains("bank", ignoreCase = true) || rail.contains("card", ignoreCase = true)) && acc.accountType.equals("BANK", ignoreCase = true) ||
                    (rail.contains("cash", ignoreCase = true)) && acc.accountType.equals("CASH", ignoreCase = true)
        }
        if (matchedAccount != null) {
            selectedAccountId = matchedAccount.id
        }

        isProcessingOcr = false
    }

    fun fallbackToLocalOcr(uri: Uri) {
        ocrStatusText = "Extracting with enterprise on-device OCR..."
        scope.launch(Dispatchers.IO) {
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val inputImage = InputImage.fromFilePath(context, uri)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        onParseText(visionText.text) { localDraft ->
                            applyDraft(localDraft, fromAi = false)
                        }
                    }
                    .addOnFailureListener { e ->
                        isProcessingOcr = false
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "OCR scanning error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } catch (e: Exception) {
                isProcessingOcr = false
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Could not load image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun processImageUri(uri: Uri) {
        isProcessingOcr = true
        ocrStatusText = "Analyzing document with Gemini Vision Intelligence..."

        // 1. Try Gemini Vision First
        if (onAnalyzeImage != null) {
            onAnalyzeImage.invoke(context, uri) { aiDraft ->
                if (aiDraft != null && (!aiDraft.merchant.isNullOrBlank() || aiDraft.amount != null)) {
                    applyDraft(aiDraft, fromAi = true)
                } else {
                    // Fallback to local OCR if AI returned null or unconfigured
                    fallbackToLocalOcr(uri)
                }
            }
        } else {
            fallbackToLocalOcr(uri)
        }
    }

    // Camera Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        FolioSecurityManager.setExternalIntentActive(false)
        if (success && photoUri != null) {
            processImageUri(photoUri)
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val tempFile = File.createTempFile("receipt_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
                photoFile = tempFile
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                photoUriString = uri.toString()
                FolioSecurityManager.setExternalIntentActive(true)
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                FolioSecurityManager.setExternalIntentActive(false)
                Toast.makeText(context, "Unable to create camera file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            FolioSecurityManager.setExternalIntentActive(false)
            Toast.makeText(context, "Camera permission is required to photograph receipts.", Toast.LENGTH_LONG).show()
        }
    }

    // Photo Picker Launcher (Gallery / Visual Media)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        FolioSecurityManager.setExternalIntentActive(false)
        if (uri != null) {
            photoUriString = uri.toString()
            processImageUri(uri)
        }
    }

    fun launchRealCamera() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            try {
                val tempFile = File.createTempFile("receipt_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
                photoFile = tempFile
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                photoUriString = uri.toString()
                FolioSecurityManager.setExternalIntentActive(true)
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                FolioSecurityManager.setExternalIntentActive(false)
                Toast.makeText(context, "Unable to create camera file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            FolioSecurityManager.setExternalIntentActive(true)
            cameraPermissionLauncher.launch(permission)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .testTag("ocr_scanner_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Receipt & Document OCR",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enterprise AI Intelligence",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryGreen
                    )
                }
                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = PrimaryGreen)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Open Camera & Select from Gallery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { launchRealCamera() },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        FolioSecurityManager.setExternalIntentActive(true)
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Photo Preview & Processing Status
            if (photoUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Receipt Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        if (isProcessingOcr) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryGreen
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = ocrStatusText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // AI Status & Confidence Badge
            if (confidenceScore != null && !isProcessingOcr) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAiVerified) PrimaryGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAiVerified) Icons.Default.AutoAwesome else Icons.Default.Receipt,
                                contentDescription = null,
                                tint = if (isAiVerified) PrimaryGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isAiVerified) "Gemini Vision Extracted ($confidenceScore%)" else "On-Device OCR ($confidenceScore%)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (taxVat > 0) {
                                    Text(
                                        text = "Included Tax/VAT: ${CurrencyUtils.format(taxVat, currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (!isAiVerified && photoUri != null) {
                            OutlinedButton(
                                onClick = {
                                    if (photoUri != null && onAnalyzeImage != null) {
                                        isProcessingOcr = true
                                        ocrStatusText = "Enhancing with Gemini Vision..."
                                        onAnalyzeImage(context, photoUri!!) { aiDraft ->
                                            if (aiDraft != null) {
                                                applyDraft(aiDraft, fromAi = true)
                                            } else {
                                                isProcessingOcr = false
                                                Toast.makeText(context, "Could not enhance: check Gemini API key", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enhance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 1. Transaction Type Selector (Expense vs Income vs Bill/Recharge vs Transfer)
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val types = listOf(
                    "EXPENSE" to "Expense",
                    "INCOME" to "Income",
                    "BILL_PAYMENT" to "Bill / Utility",
                    "TRANSFER" to "Transfer"
                )
                items(types) { (key, label) ->
                    FilterChip(
                        selected = transactionType == key,
                        onClick = { transactionType = key },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (transactionType == key) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (key) {
                                "INCOME" -> PrimaryGreen
                                "BILL_PAYMENT" -> WarningAmber
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = if (key == "INCOME") Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Merchant / Payee Name Field
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Store / Recipient Name") },
                placeholder = { Text("e.g. KFC, Shoprite, Stephen Etse") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (merchant.isNotBlank() && confidenceScore != null && confidenceScore!! >= 75) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Reference / Transaction ID / Token Field
            OutlinedTextField(
                value = referenceNumber,
                onValueChange = { referenceNumber = it },
                label = { Text("Reference / Txn ID / Receipt / Token #") },
                placeholder = { Text("e.g. EAD00605119, Token: 6957...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (referenceNumber.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Transaction Reference", referenceNumber)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Reference copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Reference", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Amount Field
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Total Amount ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Account Selection
            Text(
                text = "Charge Account / Payment Method",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts) { account ->
                    FilterChip(
                        selected = selectedAccountId == account.id,
                        onClick = { selectedAccountId = account.id },
                        label = { Text(account.name, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Category Selection
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7. Notes / Annotation
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Tokens / Details") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Save Transaction Button
            val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
            val isFormValid = merchant.isNotBlank() && parsedAmount > 0

            Button(
                onClick = {
                    val isIncome = transactionType == "INCOME"
                    val signedAmount = if (isIncome) parsedAmount else -parsedAmount

                    onSaveTransaction(
                        merchant.trim(),
                        signedAmount,
                        currency,
                        isIncome,
                        selectedAccountId,
                        selectedCategoryId,
                        notes.ifBlank { null },
                        receiptTimestamp,
                        photoFile?.absolutePath,
                        referenceNumber.trim().ifBlank { null }
                    )
                    onDismiss()
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save to Ledger", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
