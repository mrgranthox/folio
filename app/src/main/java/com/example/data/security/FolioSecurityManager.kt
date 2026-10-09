package com.example.data.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.security.MessageDigest

/**
 * Supported authentication lock mechanisms for Folio.
 */
enum class SecurityLockType {
    SYSTEM,     // Device-native credentials: Fingerprint, Face, or Device PIN / Pattern / Password
    CUSTOM_PIN  // Dedicated 4-digit custom PIN created by the user for Folio
}

/**
 * Enterprise-grade security manager for Folio application lock.
 *
 * Provides:
 * 1. Android hardware & system lock integration (Biometrics & Device PIN/Pattern via KeyguardManager).
 * 2. Secure salted SHA-256 custom PIN hashing and verification (NO hardcoded passcodes).
 * 3. User-controlled lock type preference and lifecycle state management.
 */
object FolioSecurityManager {
    private const val PREFS_NAME = "folio_security_prefs"
    private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
    private const val KEY_LOCK_TYPE = "security_lock_type"
    private const val KEY_PIN_HASH = "app_passcode_pin_hash"
    private const val KEY_LEGACY_PIN = "app_passcode_pin"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Flag indicating an in-app external activity flow is currently active
     * (e.g. Taking a camera photo, picking an image from gallery, device biometric verification).
     * When active, transient ON_STOP events from launching these system activities
     * will not trigger the app lock gate.
     */
    @Volatile
    var isExternalIntentActive: Boolean = false
        private set

    fun setExternalIntentActive(active: Boolean) {
        isExternalIntentActive = active
    }

    /**
     * Checks if the app lock gate is enabled.
     */
    fun isAppLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRICS_ENABLED, false)
    }

    /**
     * Toggles the app lock gate.
     */
    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRICS_ENABLED, enabled).apply()
    }

    /**
     * Retrieves the preferred lock method (SYSTEM vs CUSTOM_PIN).
     */
    fun getLockType(context: Context): SecurityLockType {
        val raw = getPrefs(context).getString(KEY_LOCK_TYPE, null)
        return when (raw) {
            SecurityLockType.CUSTOM_PIN.name -> SecurityLockType.CUSTOM_PIN
            SecurityLockType.SYSTEM.name -> SecurityLockType.SYSTEM
            else -> {
                // If the user already has a custom PIN configured, prefer CUSTOM_PIN; otherwise SYSTEM
                if (hasCustomPin(context)) SecurityLockType.CUSTOM_PIN else SecurityLockType.SYSTEM
            }
        }
    }

    /**
     * Updates the preferred lock method.
     */
    fun setLockType(context: Context, type: SecurityLockType) {
        getPrefs(context).edit().putString(KEY_LOCK_TYPE, type.name).apply()
    }

    /**
     * Returns true if the user has created and stored a custom PIN.
     * Note: Does NOT rely on hardcoded defaults.
     */
    fun hasCustomPin(context: Context): Boolean {
        val prefs = getPrefs(context)
        val hash = prefs.getString(KEY_PIN_HASH, null)
        if (!hash.isNullOrBlank()) return true
        val legacy = prefs.getString(KEY_LEGACY_PIN, null)
        // Disallow hardcoded legacy "1234" or "123" if accidentally saved
        return !legacy.isNullOrBlank() && legacy != "1234" && legacy != "123"
    }

    /**
     * Sets and hashes a new 4-digit custom PIN.
     */
    fun setCustomPin(context: Context, pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() }) {
            "Folio PIN must be exactly 4 numeric digits."
        }
        val hash = hashPin(pin)
        getPrefs(context).edit()
            .putString(KEY_PIN_HASH, hash)
            .remove(KEY_LEGACY_PIN) // Purge plain text legacy key
            .putString(KEY_LOCK_TYPE, SecurityLockType.CUSTOM_PIN.name)
            .apply()
    }

    /**
     * Verifies the entered PIN against the stored hash.
     * Returns false if no PIN is configured or if it does not match.
     */
    fun verifyCustomPin(context: Context, enteredPin: String): Boolean {
        if (enteredPin.isBlank()) return false
        val prefs = getPrefs(context)
        val savedHash = prefs.getString(KEY_PIN_HASH, null)
        if (!savedHash.isNullOrBlank()) {
            return savedHash == hashPin(enteredPin)
        }
        val legacy = prefs.getString(KEY_LEGACY_PIN, null)
        if (!legacy.isNullOrBlank() && legacy != "1234" && legacy != "123") {
            val matches = legacy == enteredPin
            if (matches) {
                // Automatically migrate to secure salted SHA-256 hash
                setCustomPin(context, enteredPin)
            }
            return matches
        }
        return false
    }

    /**
     * Clears any configured custom PIN.
     */
    fun clearCustomPin(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_LEGACY_PIN)
            .apply()
    }

    /**
     * Checks if the host Android device has a secure screen lock (PIN, pattern, password, or biometrics)
     * configured in device Settings.
     */
    fun isDeviceSecure(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager?.isDeviceSecure ?: false
        } else {
            keyguardManager?.isKeyguardSecure ?: false
        }
    }

    /**
     * Creates an Intent to invoke the native Android device credentials dialog
     * (Device PIN, pattern, password, or fingerprint).
     */
    fun createConfirmDeviceCredentialIntent(
        context: Context,
        title: String = "Unlock Folio",
        description: String = "Confirm your fingerprint, face, or device PIN"
    ): Intent? {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.createConfirmDeviceCredentialIntent(title, description)
    }

    /**
     * Computes a salted SHA-256 hash for secure PIN persistence.
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedBytes = ("folio_sec_salt_$pin").toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(saltedBytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
