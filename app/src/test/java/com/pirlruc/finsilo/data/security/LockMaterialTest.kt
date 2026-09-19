package com.pirlruc.finsilo.data.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LockMaterialTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var keys: DatabaseKeyStore
    private lateinit var lock: AppLockStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        prefs = context.getSharedPreferences("lock_material_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        keys = DatabaseKeyStore(prefs)
        lock = AppLockStore(prefs)
    }

    @Test
    fun provisionWithLockWritesWrapsAndHashesTogether() {
        assertTrue(keys.provisionWithLock(lock, "1234", "ABCD1234EFGH5678"))
        assertTrue(lock.isSetup())
        assertTrue(lock.verifyPin("1234"))
        assertTrue(lock.verifyRecovery("ABCD-1234-EFGH-5678"))
        assertTrue(prefs.contains("sqlcipher_wrap_pin"))
        assertTrue(prefs.contains("pin_hash"))
        val reopenedKeys = DatabaseKeyStore(prefs)
        val reopenedLock = AppLockStore(prefs)
        assertTrue(reopenedLock.verifyPin("1234"))
        assertTrue(reopenedKeys.unlockWithPin("1234"))
        assertFalse(DatabaseKeyStore(prefs).unlockWithPin("0000"))
    }

    @Test
    fun openSessionStillRejectsTheWrongPin() {
        assertTrue(keys.provision("1234", "ABCD1234EFGH5678"))
        assertTrue(keys.isSessionOpen())
        assertFalse(keys.unlockWithPin("0000"))
        assertTrue(keys.isSessionOpen())
        assertTrue(keys.unlockWithPin("1234"))
    }

    @Test
    fun rewrapPinWithLockUpdatesHashAndWrap() {
        assertTrue(keys.provisionWithLock(lock, "1234", "ABCD1234EFGH5678"))
        assertTrue(keys.rewrapPinWithLock(lock, "9999"))
        assertTrue(lock.verifyPin("9999"))
        assertFalse(lock.verifyPin("1234"))
        val reopened = DatabaseKeyStore(prefs)
        assertFalse(reopened.unlockWithPin("1234"))
        assertTrue(reopened.unlockWithPin("9999"))
    }

    @Test
    fun rewrapRecoveryWithLockUpdatesHashAndWrap() {
        assertTrue(keys.provisionWithLock(lock, "1234", "ABCD1234EFGH5678"))
        assertTrue(keys.rewrapRecoveryWithLock(lock, "ZZZZ9999YYYY8888"))
        assertTrue(lock.verifyRecovery("ZZZZ-9999-YYYY-8888"))
        assertFalse(lock.verifyRecovery("ABCD1234EFGH5678"))
        val reopened = DatabaseKeyStore(prefs)
        assertFalse(reopened.unlockWithRecovery("ABCD1234EFGH5678"))
        assertTrue(reopened.unlockWithRecovery("ZZZZ9999YYYY8888"))
    }

    @Test
    fun finishLegacyMigrationWithPinHashWritesHashAndWrap() {
        val raw = ByteArray(32) { 3 }
        prefs.edit().putString("sqlcipher_passphrase", AppLockCrypto.toHex(raw)).commit()
        assertTrue(keys.unlockWithPin("0000"))
        assertTrue(keys.finishLegacyMigrationWithPinHash(lock, "9999", "ABCD1234EFGH5678"))
        assertTrue(lock.verifyPin("9999"))
        assertFalse(prefs.contains("sqlcipher_passphrase"))
        val reopened = DatabaseKeyStore(prefs)
        assertTrue(reopened.unlockWithPin("9999"))
        assertTrue(raw.contentEquals(reopened.sessionPassphrase()))
    }
}
