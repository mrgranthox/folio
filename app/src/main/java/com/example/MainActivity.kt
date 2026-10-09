package com.example

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.security.FolioSecurityManager
import com.example.data.security.SecurityLockType
import com.example.ui.screens.MainAppShell
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.FolioTheme
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()
    private val isAppLockedState = mutableStateOf(false)
    private var isAuthenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val lockEnabled = FolioSecurityManager.isAppLockEnabled(this)
        if (lockEnabled) {
            isAppLockedState.value = true
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!isAuthenticating && FolioSecurityManager.isAppLockEnabled(this@MainActivity)) {
                    isAppLockedState.value = true
                }
            }
        })

        setContent {
            FolioTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    // Permissions handled
                }

                LaunchedEffect(Unit) {
                    val permissionsToRequest = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.READ_SMS)
                    }
                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                }

                val isLocked by remember { isAppLockedState }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isLocked) {
                        BiometricLockScreen(
                            onUnlock = { isAppLockedState.value = false },
                            onSetAuthenticating = { isAuthenticating = it }
                        )
                    } else {
                        MainAppShell(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun BiometricLockScreen(
    onUnlock: () -> Unit,
    onSetAuthenticating: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val isDeviceSecure = remember { FolioSecurityManager.isDeviceSecure(context) }
    val hasCustomPin = remember { FolioSecurityManager.hasCustomPin(context) }
    val preferredLockType = remember { FolioSecurityManager.getLockType(context) }

    // Toggle between Device Screen Lock view and Custom PIN keypad
    var showPinPad by remember {
        mutableStateOf(preferredLockType == SecurityLockType.CUSTOM_PIN && hasCustomPin)
    }

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val keyguardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onSetAuthenticating(false)
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlock()
        } else {
            errorMessage = "Device authentication not confirmed"
        }
    }

    val launchDeviceAuth: () -> Unit = {
        errorMessage = ""
        onSetAuthenticating(true)
        var launched = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val cancellationSignal = CancellationSignal()
                val biometricPrompt = BiometricPrompt.Builder(context)
                    .setTitle("Folio Security Lock")
                    .setSubtitle("Confirm your fingerprint, face, or device PIN")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                biometricPrompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            onSetAuthenticating(false)
                            onUnlock()
                        }
                        override fun onAuthenticationFailed() {
                            onSetAuthenticating(false)
                            errorMessage = "Biometric scan not recognized. Tap to retry or use device PIN."
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            onSetAuthenticating(false)
                        }
                    }
                )
                launched = true
            } catch (_: Exception) {
                launched = false
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val cancellationSignal = CancellationSignal()
                val biometricPrompt = BiometricPrompt.Builder(context)
                    .setTitle("Folio Security Lock")
                    .setSubtitle("Confirm your fingerprint, face, or device PIN")
                    .setDeviceCredentialAllowed(true)
                    .build()

                biometricPrompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            onSetAuthenticating(false)
                            onUnlock()
                        }
                        override fun onAuthenticationFailed() {
                            onSetAuthenticating(false)
                            errorMessage = "Biometric scan not recognized. Tap to retry or use device PIN."
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            onSetAuthenticating(false)
                        }
                    }
                )
                launched = true
            } catch (_: Exception) {
                launched = false
            }
        }

        if (!launched) {
            val intent = FolioSecurityManager.createConfirmDeviceCredentialIntent(context)
            if (intent != null) {
                keyguardLauncher.launch(intent)
            } else {
                onSetAuthenticating(false)
                if (hasCustomPin) {
                    showPinPad = true
                    errorMessage = "No screen lock on device. Enter your custom Folio PIN."
                } else {
                    errorMessage = "No screen lock or PIN configured on device."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!showPinPad) {
            launchDeviceAuth()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("biometric_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Section
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CreditGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showPinPad) Icons.Default.Lock else Icons.Default.Fingerprint,
                        contentDescription = "App Lock",
                        tint = CreditGreen,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (showPinPad) "Folio Passcode Lock" else "Folio Security Lock",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (showPinPad) {
                        "Enter your 4-digit security PIN to unlock"
                    } else {
                        "Confirm fingerprint, face, or device PIN to access your financial ledger"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (showPinPad) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Passcode Dot Indicators (4 dots)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) CreditGreen
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFilled) CreditGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Central / Keypad Section
            if (showPinPad) {
                // Numpad Keypad (3 columns x 4 rows)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val numpadRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("FP", "0", "DEL")
                    )

                    for (row in numpadRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(0.85f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (key) {
                                                "FP" -> CreditGreen.copy(alpha = 0.15f)
                                                "DEL" -> MaterialTheme.colorScheme.surfaceContainerHigh
                                                else -> MaterialTheme.colorScheme.surfaceContainer
                                            }
                                        )
                                        .clickable {
                                            errorMessage = ""
                                            when (key) {
                                                "FP" -> launchDeviceAuth()
                                                "DEL" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                    }
                                                }
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        val newPin = enteredPin + key
                                                        enteredPin = newPin
                                                        if (newPin.length == 4) {
                                                            if (FolioSecurityManager.verifyCustomPin(context, newPin)) {
                                                                onUnlock()
                                                            } else {
                                                                errorMessage = "Incorrect PIN. Please try again."
                                                                enteredPin = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (key) {
                                        "FP" -> Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Use Device Lock",
                                            tint = CreditGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        "DEL" -> Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete Digit",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        else -> Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 24.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isDeviceSecure) {
                        TextButton(
                            onClick = {
                                showPinPad = false
                                launchDeviceAuth()
                            }
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Device Screen Lock / Biometrics")
                        }
                    }
                }
            } else {
                // Device Screen Lock Mode
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = launchDeviceAuth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Unlock with Device Lock",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Utilizes your device's fingerprint, face, or system PIN / pattern",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (hasCustomPin) {
                        OutlinedButton(
                            onClick = {
                                enteredPin = ""
                                errorMessage = ""
                                showPinPad = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter Custom Folio PIN")
                        }
                    }
                }
            }
        }
    }
}
