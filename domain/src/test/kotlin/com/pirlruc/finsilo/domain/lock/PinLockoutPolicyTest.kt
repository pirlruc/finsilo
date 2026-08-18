package com.pirlruc.finsilo.domain.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinLockoutPolicyTest {
    @Test
    fun noLockoutBeforeThreshold() {
        assertEquals(0L, PinLockoutPolicy.lockoutMs(0))
        assertEquals(0L, PinLockoutPolicy.lockoutMs(PinLockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT - 1))
        assertFalse(PinLockoutPolicy.isLockedOut(10L, 10L))
        assertFalse(PinLockoutPolicy.isLockedOut(11L, 10L))
    }

    @Test
    fun doublesAfterThresholdUntilCap() {
        assertEquals(PinLockoutPolicy.INITIAL_LOCKOUT_MS, PinLockoutPolicy.lockoutMs(5))
        assertEquals(60_000L, PinLockoutPolicy.lockoutMs(6))
        assertEquals(120_000L, PinLockoutPolicy.lockoutMs(7))
        assertEquals(PinLockoutPolicy.MAX_LOCKOUT_MS, PinLockoutPolicy.lockoutMs(20))
        assertEquals(1_000L, PinLockoutPolicy.remainingMs(9_000L, 10_000L))
        assertTrue(PinLockoutPolicy.isLockedOut(9_000L, 10_000L))
    }
}
