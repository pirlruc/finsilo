package com.pirlruc.finsilo.data.security

import javax.crypto.KeyGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsAliasTest {
    @Test
    fun debugDoesNotReadTheReleaseAlias() {
        val debug = PrefsKeyAlias.readable(debug = true)
        assertTrue(debug.contains(PrefsKeyAlias.DEBUG))
        assertTrue(debug.contains(PrefsKeyAlias.LEGACY))
        assertFalse(debug.contains(PrefsKeyAlias.RELEASE))
        assertFalse(PrefsKeyAlias.readable(debug = false).contains(PrefsKeyAlias.DEBUG))
    }

    @Test
    fun legacyCiphertextOpensAndResealsOnTheCurrentKey() {
        val current = AesGcmPrefsAead(key())
        val legacy = AesGcmPrefsAead(key())
        val migrating = MigratingPrefsAead(current, legacy)
        val associated = "vault".toByteArray()
        val sealed = legacy.seal(associated, "secret".toByteArray())
        val opened = migrating.open(associated, sealed)
        assertTrue(opened.contentEquals("secret".toByteArray()))
        val resealed = migrating.reseal(associated, sealed, opened!!)
        assertTrue(current.open(associated, resealed!!)!!.contentEquals("secret".toByteArray()))
        assertTrue(migrating.reseal(associated, resealed, opened) == null)
    }

    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
