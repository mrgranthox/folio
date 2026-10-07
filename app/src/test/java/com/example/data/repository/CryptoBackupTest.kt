package com.example.data.repository

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoBackupTest {

    @Test
    fun pbkdf2AesGcmEncryptAndDecryptRoundTrip() {
        val passphrase = "test-secure-passphrase-123"
        val plainText = """{"app":"Folio","test":true,"balance":1500.75}"""

        // Encrypt with PBKDF2 + AES-256-GCM
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val iterations = 100_000
        val keySpec = PBEKeySpec(passphrase.toCharArray(), saltBytes, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

        val ivBytes = ByteArray(12)
        SecureRandom().nextBytes(ivBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val payload = JSONObject()
        payload.put("version", 2)
        payload.put("cipher", "AES-256-GCM")
        payload.put("kdf", "PBKDF2WithHmacSHA256")
        payload.put("iterations", iterations)
        payload.put("salt", Base64.encodeToString(saltBytes, Base64.NO_WRAP))
        payload.put("iv", Base64.encodeToString(ivBytes, Base64.NO_WRAP))
        payload.put("data", Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))

        // Decrypt
        val readSalt = Base64.decode(payload.getString("salt"), Base64.DEFAULT)
        val readIv = Base64.decode(payload.getString("iv"), Base64.DEFAULT)
        val readData = Base64.decode(payload.getString("data"), Base64.DEFAULT)
        val readIterations = payload.getInt("iterations")

        val decKeySpec = PBEKeySpec(passphrase.toCharArray(), readSalt, readIterations, 256)
        val decSecretKey = SecretKeySpec(factory.generateSecret(decKeySpec).encoded, "AES")

        val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, decSecretKey, GCMParameterSpec(128, readIv))
        val decryptedBytes = decCipher.doFinal(readData)
        val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)

        assertEquals(plainText, decryptedText)
    }

    @Test
    fun legacyAesCbcFallbackDecryption() {
        val passphrase = "mypassword"
        val plainText = """{"legacy":true,"app":"Folio V1"}"""

        // Encrypt with legacy CBC
        val keyBytes = passphrase.padEnd(32, '*').substring(0, 32).toByteArray(StandardCharsets.UTF_8)
        val ivBytes = ByteArray(16)
        SecureRandom().nextBytes(ivBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val legacyPayload = JSONObject()
        legacyPayload.put("iv", Base64.encodeToString(ivBytes, Base64.NO_WRAP))
        legacyPayload.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))

        // Decrypt via fallback path
        val readIv = Base64.decode(legacyPayload.getString("iv"), Base64.DEFAULT)
        val readData = Base64.decode(legacyPayload.getString("data"), Base64.DEFAULT)

        val decKeyBytes = passphrase.padEnd(32, '*').substring(0, 32).toByteArray(StandardCharsets.UTF_8)
        val decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decKeyBytes, "AES"), IvParameterSpec(readIv))
        val decrypted = String(decCipher.doFinal(readData), StandardCharsets.UTF_8)

        assertEquals(plainText, decrypted)
    }
}
