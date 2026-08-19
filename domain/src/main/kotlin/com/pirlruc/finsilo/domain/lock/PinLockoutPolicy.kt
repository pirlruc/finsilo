package com.pirlruc.finsilo.domain.lock

/**
 * Cooldown after repeated failed PIN or recovery attempts.
 *
 * Five failures start a 30-second lockout that doubles on each further failure,
 * capped at 15 minutes. Success clears the counter in the store, not here.
 */
object PinLockoutPolicy {
    /** Consecutive failures allowed before the first cooldown. */
    const val ATTEMPTS_BEFORE_LOCKOUT: Int = 5

    /** First cooldown after [ATTEMPTS_BEFORE_LOCKOUT] failures, in milliseconds. */
    const val INITIAL_LOCKOUT_MS: Long = 30_000L

    /** Maximum cooldown, in milliseconds. */
    const val MAX_LOCKOUT_MS: Long = 15 * 60 * 1000L

    /**
     * Cooldown length after [failedAttempts] consecutive failures.
     * Zero before [ATTEMPTS_BEFORE_LOCKOUT].
     */
    fun lockoutMs(failedAttempts: Int): Long {
        if (failedAttempts < ATTEMPTS_BEFORE_LOCKOUT) return 0L
        val shift = (failedAttempts - ATTEMPTS_BEFORE_LOCKOUT).coerceAtMost(16)
        return (INITIAL_LOCKOUT_MS shl shift).coerceAtMost(MAX_LOCKOUT_MS)
    }

    /** Remaining cooldown at [nowMs]; zero when not locked out. */
    fun remainingMs(nowMs: Long, lockoutUntilMs: Long): Long = (lockoutUntilMs - nowMs).coerceAtLeast(0L)

    /** True when [lockoutUntilMs] is still in the future. */
    fun isLockedOut(nowMs: Long, lockoutUntilMs: Long): Boolean = remainingMs(nowMs, lockoutUntilMs) > 0L
}
