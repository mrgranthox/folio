package com.example.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolioSecurityManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("folio_security_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun appLockIsDisabledByDefault() {
        assertFalse(FolioSecurityManager.isAppLockEnabled(context))
        assertFalse(FolioSecurityManager.hasCustomPin(context))
        assertEquals(SecurityLockType.SYSTEM, FolioSecurityManager.getLockType(context))
    }

    @Test
    fun togglingAppLockPersistsCorrectly() {
        FolioSecurityManager.setAppLockEnabled(context, true)
        assertTrue(FolioSecurityManager.isAppLockEnabled(context))

        FolioSecurityManager.setAppLockEnabled(context, false)
        assertFalse(FolioSecurityManager.isAppLockEnabled(context))
    }

    @Test
    fun settingAndVerifyingCustomPinWorksCorrectly() {
        // Given no pin is set
        assertFalse(FolioSecurityManager.hasCustomPin(context))
        assertFalse(FolioSecurityManager.verifyCustomPin(context, "1234"))
        assertFalse(FolioSecurityManager.verifyCustomPin(context, "123"))

        // When user creates a custom PIN "7890"
        FolioSecurityManager.setCustomPin(context, "7890")

        // Then
        assertTrue(FolioSecurityManager.hasCustomPin(context))
        assertEquals(SecurityLockType.CUSTOM_PIN, FolioSecurityManager.getLockType(context))
        assertTrue(FolioSecurityManager.verifyCustomPin(context, "7890"))

        // And other/hardcoded PINs must fail
        assertFalse(FolioSecurityManager.verifyCustomPin(context, "1234"))
        assertFalse(FolioSecurityManager.verifyCustomPin(context, "123"))
        assertFalse(FolioSecurityManager.verifyCustomPin(context, "0000"))
        assertFalse(FolioSecurityManager.verifyCustomPin(context, ""))
    }

    @Test
    fun changingLockTypeWorksCorrectly() {
        FolioSecurityManager.setLockType(context, SecurityLockType.SYSTEM)
        assertEquals(SecurityLockType.SYSTEM, FolioSecurityManager.getLockType(context))

        FolioSecurityManager.setLockType(context, SecurityLockType.CUSTOM_PIN)
        assertEquals(SecurityLockType.CUSTOM_PIN, FolioSecurityManager.getLockType(context))
    }

    @Test
    fun clearingCustomPinRemovesStoredHash() {
        FolioSecurityManager.setCustomPin(context, "5544")
        assertTrue(FolioSecurityManager.hasCustomPin(context))

        FolioSecurityManager.clearCustomPin(context)
        assertFalse(FolioSecurityManager.hasCustomPin(context))
        assertFalse(FolioSecurityManager.verifyCustomPin(context, "5544"))
    }
}
