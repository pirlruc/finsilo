package com.pirlruc.finsilo.data.security

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppLockStoreTest {
    private val recovery = "ABCD1234EFGH5678"

    @Test
    fun setupVerifyResetRotateAndLockout() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("lock-store", Context.MODE_PRIVATE)
        var elapsed = 1_000L
        val store = AppLockStore(prefs) { LockoutSample(wallMs = 5_000L, elapsedMs = elapsed, bootCount = 3) }

        assertFalse(store.isSetup())
        assertFalse(store.setup("12", recovery, biometric = true))
        assertTrue(store.setup("1234", recovery, biometric = true))
        assertTrue(store.isSetup())
        assertTrue(store.biometricEnabled())
        assertTrue(store.verifyPin("1234"))
        assertFalse(store.verifyPin("9999"))
        assertTrue(store.verifyRecovery("abcd1234efgh5678"))
        assertFalse(store.verifyRecovery("nope"))

        assertFalse(store.resetPin("1"))
        assertTrue(store.resetPin("5678"))
        assertTrue(store.verifyPin("5678"))
        assertFalse(store.rotateRecovery("short"))
        assertTrue(store.rotateRecovery("ZZZZ9999YYYY8888"))
        assertTrue(store.verifyRecovery("ZZZZ9999YYYY8888"))

        assertTrue(store.setBiometricEnabled(false))
        assertFalse(store.biometricEnabled())
        repeat(5) { store.recordFailedUnlock(10_000L) }
        assertEquals(5, store.failedUnlockAttempts())
        assertTrue(store.pinLockoutUntilMs() > 10_000L)
        assertTrue(store.lockoutElapsedUntilMs() > elapsed)
        assertEquals(3, store.lockoutBootCount())
        assertTrue(store.lockoutRemaining(10_000L) > 0L)
        elapsed = store.lockoutElapsedUntilMs()
        assertEquals(0L, store.lockoutRemaining(store.pinLockoutUntilMs()))
        store.clearUnlockFailures()
        assertEquals(0, store.failedUnlockAttempts())
        assertEquals(0L, store.pinLockoutUntilMs())
    }

    @Test
    fun verifyFailsWhenTheStoredSaltIsNotHex() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("lock-bad", Context.MODE_PRIVATE)
        val store = AppLockStore(prefs)
        assertTrue(store.setup("1234", recovery, biometric = false))
        prefs.edit().putString("pin_salt", "zz").commit()
        assertFalse(store.verifyPin("1234"))
        prefs.edit().putString("pin_salt", AppLockCrypto.toHex(AppLockCrypto.generateSalt())).putString("pin_hash", "zz").commit()
        assertFalse(store.verifyPin("1234"))
    }
}
