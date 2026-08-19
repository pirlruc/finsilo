package com.pirlruc.finsilo.domain.lock

import java.security.SecureRandom
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassphraseWrapTest {
    @Test
    fun wrapThenUnwrapRoundTripsWithTheSameSecret() {
        val key = ByteArray(32) { index -> index.toByte() }
        val blob = PassphraseWrap.wrap("2468", key, SecureRandom(byteArrayOf(1, 2, 3, 4)))
        val opened = checkNotNull(PassphraseWrap.unwrap("2468", blob))
        assertTrue(key.contentEquals(opened))
    }

    @Test
    fun wrongSecretAndTruncatedBlobReturnNull() {
        val key = ByteArray(32) { 9 }
        val blob = PassphraseWrap.wrap("1234", key)
        assertNull(PassphraseWrap.unwrap("0000", blob))
        assertNull(PassphraseWrap.unwrap("1234", blob.copyOf(10)))
        assertNull(PassphraseWrap.unwrap("1234", ByteArray(0)))
        assertFalse(blob.contentEquals(PassphraseWrap.wrap("1234", key)))
    }

    @Test
    fun recoverySecretRoundTripsAfterNormalize() {
        val key = ByteArray(32) { 3 }
        val recovery = AppLockCrypto.normalizeRecovery("ABCD-1234-EFGH-5678")
        val blob = PassphraseWrap.wrap(recovery, key)
        assertTrue(key.contentEquals(checkNotNull(PassphraseWrap.unwrap(recovery, blob))))
        assertNull(PassphraseWrap.unwrap("ABCD1234EFGH0000", blob))
    }
}
