package com.pirlruc.finsilo.domain.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLockCryptoTest {
    @Test
    fun pinRulesAndRoundTripHash() {
        assertFalse(AppLockCrypto.pinOk("123"))
        assertFalse(AppLockCrypto.pinOk("123456789"))
        assertFalse(AppLockCrypto.pinOk("12ab"))
        assertTrue(AppLockCrypto.pinOk("1234"))
        assertTrue(AppLockCrypto.pinOk("12345678"))
        val salt = ByteArray(AppLockCrypto.SALT_BYTES) { 7 }
        val hash = AppLockCrypto.hashSecret("2468", salt)
        assertTrue(AppLockCrypto.verify("2468", salt, hash))
        assertFalse(AppLockCrypto.verify("0000", salt, hash))
    }

    @Test
    fun recoveryCodeNormalizesAndHexRoundTrips() {
        val code = AppLockCrypto.generateRecoveryCode()
        assertTrue(code.matches(Regex("[0-9A-F]{4}(-[0-9A-F]{4})+")))
        assertNotEquals(code, AppLockCrypto.generateRecoveryCode())
        val normalized = AppLockCrypto.normalizeRecovery("ab cd-ef")
        assertEquals("ABCDEF", normalized)
        val bytes = byteArrayOf(0x0a, 0xff.toByte())
        val hex = AppLockCrypto.toHex(bytes)
        assertEquals("0aff", hex)
        assertTrue(AppLockCrypto.fromHex(hex).contentEquals(bytes))
        assertNull(AppLockCrypto.fromHex("gg"))
        assertNull(AppLockCrypto.fromHex("abc"))
        assertNull(AppLockCrypto.fromHex(""))
    }

    @Test
    fun generateSaltHasConfiguredLength() {
        val salt = AppLockCrypto.generateSalt()
        assertEquals(AppLockCrypto.SALT_BYTES, salt.size)
        assertFalse(salt.contentEquals(ByteArray(AppLockCrypto.SALT_BYTES)))
    }
}
