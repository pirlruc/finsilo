package com.pirlruc.finsilo.data.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
    }

    @Test
    fun xmlDoesNotContainPlaintextValues() {
        val name = uniqueName()
        val prefs = SecurePreferences.open(context(), name, softwareAead())
        prefs.edit().putString("sqlcipher_wrap_pin", "wrap-hex-secret").commit()
        val xml = File(context().applicationInfo.dataDir, "shared_prefs/$name.xml")
        assertFalse(xml.readText().contains("wrap-hex-secret"))
        assertNotEquals("wrap-hex-secret", rawDelegate(name).getString("sqlcipher_wrap_pin", null))
    }

    @Test
    fun clearWipesUserKeys() {
        val name = uniqueName()
        val aead = softwareAead()
        val prefs = SecurePreferences.open(context(), name, aead)
        prefs.edit().putString("wrap", "secret").commit()
        prefs.edit().clear().commit()
        assertNull(prefs.getString("wrap", null))
        assertFalse(prefs.contains("wrap"))
        assertNull(SecurePreferences.open(context(), name, aead).getString("wrap", null))
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

    private fun rawDelegate(logicalName: String): SharedPreferences = context().getSharedPreferences(logicalName, Context.MODE_PRIVATE)
}
