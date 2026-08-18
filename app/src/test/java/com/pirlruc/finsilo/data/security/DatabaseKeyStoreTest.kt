package com.pirlruc.finsilo.data.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DatabaseKeyStoreTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var keys: DatabaseKeyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        prefs = context.getSharedPreferences("db_key_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        keys = DatabaseKeyStore(prefs)
    }

    @Test
    fun provisionWrapsKeyAndNeverWritesLegacyHex() {
        assertTrue(keys.provision("1234", "ABCD-1234-EFGH-5678"))
        assertFalse(prefs.contains("sqlcipher_passphrase"))
        assertTrue(prefs.contains("sqlcipher_wrap_pin"))
        assertTrue(prefs.contains("sqlcipher_wrap_recovery"))
        val first = keys.sessionPassphrase()
        assertEquals(32, first.size)
        val reopened = DatabaseKeyStore(prefs)
        assertFalse(reopened.isSessionOpen())
        assertTrue(reopened.unlockWithPin("1234"))
        assertTrue(first.contentEquals(reopened.sessionPassphrase()))
        assertFalse(DatabaseKeyStore(prefs).unlockWithPin("0000"))
        assertTrue(prefs.contains("sqlcipher_wrap_pin"))
    }

    @Test
    fun recoveryUnlocksAndRewrapPinRejectsOldPin() {
        assertTrue(keys.provision("1234", "ABCD1234EFGH5678"))
        val reopened = DatabaseKeyStore(prefs)
        assertTrue(reopened.unlockWithRecovery("ABCD-1234-EFGH-5678"))
        assertTrue(reopened.rewrapPin("9999"))
        val afterRewrap = DatabaseKeyStore(prefs)
        assertFalse(afterRewrap.unlockWithPin("1234"))
        assertTrue(afterRewrap.unlockWithPin("9999"))
    }

    @Test
    fun rewrapRecoveryRejectsPreviousCode() {
        assertTrue(keys.provision("1234", "ABCD1234EFGH5678"))
        assertTrue(keys.rewrapRecovery("ZZZZ9999YYYY8888"))
        val reopened = DatabaseKeyStore(prefs)
        assertFalse(reopened.unlockWithRecovery("ABCD1234EFGH5678"))
        assertTrue(reopened.unlockWithRecovery("ZZZZ-9999-YYYY-8888"))
    }

    @Test
    fun legacyUnlockKeepsHexUntilMigrationFinishes() {
        val raw = ByteArray(32) { 7 }
        prefs.edit().putString("sqlcipher_passphrase", AppLockCrypto.toHex(raw)).commit()
        assertTrue(keys.unlockWithPin("1234"))
        assertTrue(keys.needsWrapUpgrade())
        assertTrue(prefs.contains("sqlcipher_passphrase"))
        val interrupted = DatabaseKeyStore(prefs)
        assertTrue(interrupted.hasLegacyPassphrase())
        assertTrue(keys.finishLegacyMigration("1234", "ABCD1234EFGH5678"))
        assertFalse(prefs.contains("sqlcipher_passphrase"))
        val migrated = DatabaseKeyStore(prefs)
        assertFalse(migrated.needsWrapUpgrade())
        assertTrue(migrated.unlockWithPin("1234"))
        assertTrue(raw.contentEquals(migrated.sessionPassphrase()))
    }

    @Test
    fun provisionRefusesWhenLegacyHexExists() {
        prefs.edit().putString("sqlcipher_passphrase", AppLockCrypto.toHex(ByteArray(32) { 1 })).commit()
        assertFalse(keys.provision("1234", "ABCD1234EFGH5678"))
        assertTrue(prefs.contains("sqlcipher_passphrase"))
        assertFalse(prefs.contains("sqlcipher_wrap_pin"))
    }

    @Test
    fun leftoverLegacyHexIsDroppedAfterWrappedUnlock() {
        assertTrue(keys.provision("1234", "ABCD1234EFGH5678"))
        prefs.edit().putString("sqlcipher_passphrase", AppLockCrypto.toHex(ByteArray(32) { 2 })).commit()
        val reopened = DatabaseKeyStore(prefs)
        assertTrue(reopened.unlockWithPin("1234"))
        assertFalse(prefs.contains("sqlcipher_passphrase"))
    }

    @Test
    fun sessionPassphraseFailsClosedWhenLocked() {
        val error = runCatching { keys.sessionPassphrase() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun alphaVantageKeyRoundTrips() {
        keys.setAlphaVantageKey(" demo-key ")
        assertEquals("demo-key", keys.alphaVantageKey())
    }
}
