package com.pirlruc.finsilo.ui.lock

import com.pirlruc.finsilo.data.security.AppLockRepository
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PinLockoutPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun fiveFailedPinsStartLockoutThenCorrectPinWorksAfterCooldown() {
        val store = FakeAppLock()
        var now = 1_000_000L
        val viewModel = LockViewModel(store, dispatcher, { now })
        repeat(PinLockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT) {
            viewModel.setPin("0000")
            viewModel.unlockWithPin()
        }
        assertFalse(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.error!!.contains("Too many"))
        viewModel.setPin("1234")
        viewModel.unlockWithPin()
        assertFalse(viewModel.state.value.unlocked)
        now += PinLockoutPolicy.INITIAL_LOCKOUT_MS + 1
        viewModel.setPin("1234")
        viewModel.unlockWithPin()
        assertTrue(viewModel.state.value.unlocked)
        assertEquals(0, store.failedUnlockAttempts())
    }

    @Test
    fun rotateRecoveryRequiresPin() {
        val store = FakeAppLock()
        val viewModel = LockViewModel(store, dispatcher, { 1L })
        viewModel.unlockWithPinGiven("1234")
        viewModel.requestRotateRecovery()
        assertEquals(SensitiveLockAction.ROTATE_RECOVERY, viewModel.state.value.pendingSensitiveAction)
        viewModel.setPin("0000")
        viewModel.confirmSensitiveAction()
        assertNull(viewModel.state.value.newRecoveryCode)
        viewModel.setPin("1234")
        viewModel.confirmSensitiveAction()
        assertTrue(viewModel.state.value.newRecoveryCode != null)
        assertTrue(store.recovery != FakeAppLock.INITIAL_RECOVERY)
    }

    @Test
    fun backgroundRelockKeepsUnsavedRecoveryCode() {
        val store = FakeAppLock()
        val viewModel = LockViewModel(store, dispatcher, { 1L })
        viewModel.unlockWithPinGiven("1234")
        viewModel.requestRotateRecovery()
        viewModel.setPin("1234")
        viewModel.confirmSensitiveAction()
        val code = viewModel.state.value.newRecoveryCode
        assertTrue(code != null)
        viewModel.onAppBackgrounded()
        assertFalse(viewModel.state.value.unlocked)
        assertEquals(code, viewModel.state.value.newRecoveryCode)
    }

    @Test
    fun backgroundDoesNotLockDuringBiometricPrompt() {
        val store = FakeAppLock()
        val viewModel = LockViewModel(store, dispatcher, { 1L })
        viewModel.unlockWithPinGiven("1234")
        viewModel.setBiometricPromptActive(true)
        viewModel.onAppBackgrounded()
        assertTrue(viewModel.state.value.unlocked)
    }

    private fun LockViewModel.unlockWithPinGiven(pin: String) {
        setPin(pin)
        unlockWithPin()
        assertTrue(state.value.unlocked)
    }
}

private class FakeAppLock : AppLockRepository {
    var pin: String = "1234"
    var recovery: String = INITIAL_RECOVERY
    private var setup = true
    private var biometric = false
    private var attempts = 0
    private var lockoutUntil = 0L

    override fun isSetup(): Boolean = setup

    override fun biometricEnabled(): Boolean = biometric

    override fun setBiometricEnabled(enabled: Boolean) {
        biometric = enabled
    }

    override fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean {
        if (!AppLockCrypto.pinOk(pin)) return false
        this.pin = pin
        recovery = AppLockCrypto.normalizeRecovery(recoveryCode)
        this.biometric = biometric
        setup = true
        clearUnlockFailures()
        return true
    }

    override fun verifyPin(candidate: String): Boolean = candidate == pin

    override fun verifyRecovery(code: String): Boolean = AppLockCrypto.normalizeRecovery(code) == recovery

    override fun resetPin(newPin: String): Boolean {
        if (!AppLockCrypto.pinOk(newPin)) return false
        pin = newPin
        clearUnlockFailures()
        return true
    }

    override fun rotateRecovery(newCode: String): Boolean {
        val normalized = AppLockCrypto.normalizeRecovery(newCode)
        if (normalized.length < 16) return false
        recovery = normalized
        return true
    }

    override fun failedUnlockAttempts(): Int = attempts

    override fun pinLockoutUntilMs(): Long = lockoutUntil

    override fun recordFailedUnlock(nowMs: Long) {
        attempts += 1
        lockoutUntil = nowMs + PinLockoutPolicy.lockoutMs(attempts)
    }

    override fun clearUnlockFailures() {
        attempts = 0
        lockoutUntil = 0L
    }

    companion object {
        const val INITIAL_RECOVERY: String = "ABCD1234EFGH5678"
    }
}
