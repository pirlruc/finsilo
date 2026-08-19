package com.pirlruc.finsilo.data.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SecurePreferencesTest {
    @Test
    fun roundTripsStringBooleanIntAndLong() {
        val prefs = SecurePreferences.open(context(), uniqueName(), softwareAead())
        assertTrue(
            prefs.edit()
                .putString("wrap", "deadbeef")
                .putBoolean("biometric", true)
                .putInt("attempts", 4)
                .putLong("until", 99L)
                .commit(),
        )
        assertEquals("deadbeef", prefs.getString("wrap", null))
        assertTrue(prefs.getBoolean("biometric", false))
        assertEquals(4, prefs.getInt("attempts", 0))
        assertEquals(99L, prefs.getLong("until", 0L))
        assertTrue(prefs.contains("wrap"))
        assertFalse(prefs.contains(SecurePreferences.FORMAT_MARKER))
    }

    @Test
    fun xmlDoesNotContainPlaintextValues() {
        val name = uniqueName()
        val aead = softwareAead()
        val prefs = SecurePreferences.open(context(), name, aead)
        prefs.edit().putString("sqlcipher_wrap_pin", "wrap-hex-secret").commit()
        val xml = EncryptedSharedPreferencesMigrator.prefsXml(context(), SecurePreferences.storageName(name))
        val text = xml.readText()
        assertTrue(text.contains(SecurePreferences.FORMAT_MARKER))
        assertFalse(text.contains("wrap-hex-secret"))
        assertNotEquals("wrap-hex-secret", rawDelegate(name).getString("sqlcipher_wrap_pin", null))
    }

    @Test
    fun migratesEncryptedSharedPreferencesWithoutWipe() {
        val name = uniqueName()
        EncryptedSharedPreferencesMigrator.openLegacyEsp(context(), name)
            .edit()
            .putString("sqlcipher_wrap_pin", "wrap-hex")
            .putBoolean("biometric", true)
            .putInt("failed_unlock_attempts", 3)
            .putLong("pin_lockout_until_ms", 42L)
            .commit()
        val migrated = SecurePreferences.open(context(), name)
        assertEquals("wrap-hex", migrated.getString("sqlcipher_wrap_pin", null))
        assertTrue(migrated.getBoolean("biometric", false))
        assertEquals(3, migrated.getInt("failed_unlock_attempts", 0))
        assertEquals(42L, migrated.getLong("pin_lockout_until_ms", 0L))
        val again = SecurePreferences.open(context(), name)
        assertEquals("wrap-hex", again.getString("sqlcipher_wrap_pin", null))
        assertFalse(EncryptedSharedPreferencesMigrator.prefsXml(context(), name).exists())
    }

    @Test
    fun productionOpenRoundTripsThroughAndroidKeystore() {
        val prefs = SecurePreferences.open(context(), uniqueName())
        assertTrue(prefs.edit().putString("av", "demo-key").putBoolean("flag", false).commit())
        assertEquals("demo-key", prefs.getString("av", null))
        assertFalse(prefs.getBoolean("flag", true))
    }

    @Test
    fun clearWipesUserKeysAndKeepsFormat() {
        val name = uniqueName()
        val aead = softwareAead()
        val prefs = SecurePreferences.open(context(), name, aead)
        prefs.edit().putString("wrap", "secret").commit()
        prefs.edit().clear().commit()
        assertNull(prefs.getString("wrap", null))
        assertFalse(prefs.contains("wrap"))
        val reopened = SecurePreferences.open(context(), name, aead)
        assertNull(reopened.getString("wrap", null))
        assertTrue(
            rawDelegate(name).contains(SecurePreferences.FORMAT_MARKER),
        )
    }

    @Test
    fun missingKeysReturnDefaults() {
        val prefs = SecurePreferences.open(context(), uniqueName(), softwareAead())
        assertNull(prefs.getString("missing", null))
        assertEquals("fallback", prefs.getString("missing", "fallback"))
        assertFalse(prefs.getBoolean("missing", false))
        assertEquals(7, prefs.getInt("missing", 7))
        assertEquals(8L, prefs.getLong("missing", 8L))
    }

    @Test
    fun tamperedBlobFailsClosed() {
        val name = uniqueName()
        val prefs = SecurePreferences.open(context(), name, softwareAead())
        prefs.edit().putString("wrap", "secret").commit()
        rawDelegate(name).edit().putString("wrap", "AAAA").commit()
        assertNull(prefs.getString("wrap", null))
        assertEquals("fallback", prefs.getString("wrap", "fallback"))
    }

    private fun context(): Application = ApplicationProvider.getApplicationContext()

    private fun uniqueName(): String = "sp_${System.nanoTime()}"

    private fun softwareAead(): PrefsAead {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        return AesGcmPrefsAead(generator.generateKey())
    }

    private fun rawDelegate(logicalName: String): SharedPreferences =
        context().getSharedPreferences(SecurePreferences.storageName(logicalName), Context.MODE_PRIVATE)
}
