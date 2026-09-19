package com.pirlruc.finsilo.ui.lock

import com.pirlruc.finsilo.data.security.AppLockRepository
import com.pirlruc.finsilo.data.security.LedgerKeySession
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PinLockoutPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun emptyPinDoesNotUnlockOrCountAsFailure() {
        val store = FakeAppLock()
        val viewModel = LockViewModel(store, dispatcher, { 1L })
        viewModel.unlockWithPin()
        assertFalse(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.error!!.contains("4–8"))
        assertEquals(0, store.failedUnlockAttempts())
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

    @Test
    fun backgroundDoesNotLockWhileFilePickerIsOpen() {
        val store = FakeAppLock()
        val viewModel = LockViewModel(store, dispatcher, { 1L })
        viewModel.unlockWithPinGiven("1234")
        viewModel.setExternalUiActive(true)
        viewModel.onAppBackgrounded()
        assertTrue(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.externalUiActive)
        viewModel.setExternalUiActive(false)
        assertFalse(viewModel.state.value.externalUiActive)
        viewModel.onAppBackgrounded()
        assertFalse(viewModel.state.value.unlocked)
    }

    @Test
    fun overlappingPickersKeepSessionUntilTheLastCloses() {
        val store = FakeAppLock()
        val viewModel = LockViewModel(store, dispatcher, { 1L })
        viewModel.unlockWithPinGiven("1234")
        viewModel.setExternalUiActive(true)
        viewModel.setExternalUiActive(true)
        viewModel.setExternalUiActive(false)
        viewModel.onAppBackgrounded()
        assertTrue(viewModel.state.value.unlocked)
        viewModel.setExternalUiActive(false)
        viewModel.onAppBackgrounded()
        assertFalse(viewModel.state.value.unlocked)
    }

    @Test
    fun completeSetupProvisionsWrappedKey() {
        val keys = FakeLedgerKeys()
        var opened = 0
        val viewModel = LockViewModel(FakeAppLock(setup = false), dispatcher, { 1L }, keys, openLedger = { opened += 1 })
        viewModel.setPin("1234")
        viewModel.setPinConfirm("1234")
        viewModel.setRecoveryConfirm(true)
        viewModel.completeSetup()
        assertTrue(viewModel.state.value.unlocked)
        assertEquals(1, keys.provisionCalls)
        assertEquals(1, opened)
    }

    @Test
    fun completeSetupRollsBackWrapsWhenLockStoreFails() {
        val keys = FakeLedgerKeys()
        val viewModel = LockViewModel(FakeAppLock(setup = false, failSetup = true), dispatcher, { 1L }, keys)
        viewModel.setPin("1234")
        viewModel.setPinConfirm("1234")
        viewModel.setRecoveryConfirm(true)
        viewModel.completeSetup()
        assertFalse(viewModel.state.value.setupComplete)
        assertFalse(viewModel.state.value.unlocked)
        assertEquals(1, keys.provisionCalls)
        assertEquals(1, keys.evictCalls)
        assertTrue(keys.discardCalls >= 2)
        assertFalse(keys.isSessionOpen())
        assertEquals("Could not store the lock.", viewModel.state.value.error)
    }

    @Test
    fun biometricColdStartRequiresPin() {
        val keys = FakeLedgerKeys()
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L }, keys)
        viewModel.unlockWithBiometric()
        assertFalse(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.error!!.contains("PIN"))
    }

    @Test
    fun biometricWorksWhenSessionAlreadyOpen() {
        val keys = FakeLedgerKeys(sessionOpen = true)
        var opened = 0
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L }, keys, openLedger = { opened += 1 })
        viewModel.unlockWithBiometric()
        assertTrue(viewModel.state.value.unlocked)
        assertEquals(1, opened)
    }

    @Test
    fun pinUnlockOnLegacyShowsUpgradeUntilConfirm() {
        val keys = FakeLedgerKeys(upgrade = true)
        var opened = 0
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L }, keys, openLedger = { opened += 1 })
        viewModel.setPin("1234")
        viewModel.unlockWithPin()
        assertTrue(viewModel.state.value.wrapUpgradeRequired)
        assertFalse(viewModel.state.value.unlocked)
        assertEquals(0, opened)
        viewModel.completeWrapUpgrade()
        assertFalse(viewModel.state.value.unlocked)
        assertEquals(0, keys.finishMigrationCalls)
        viewModel.setUpgradeRecoveryConfirm(true)
        viewModel.completeWrapUpgrade()
        assertTrue(viewModel.state.value.unlocked)
        assertEquals(1, keys.finishMigrationCalls)
        assertEquals(1, opened)
    }

    @Test
    fun rotateRecoveryRewrapsKey() {
        val keys = FakeLedgerKeys(sessionOpen = true)
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L }, keys)
        viewModel.unlockWithPinGiven("1234")
        viewModel.requestRotateRecovery()
        viewModel.setPin("1234")
        viewModel.confirmSensitiveAction()
        assertTrue(keys.lastRewrapRecovery != null)
    }

    @Test
    fun provisionFailureDoesNotMarkLockReady() {
        val store = FakeAppLock(setup = false)
        val keys = FakeLedgerKeys(provisionOk = false)
        val viewModel = LockViewModel(store, dispatcher, { 1L }, keys)
        viewModel.setPin("1234")
        viewModel.setPinConfirm("1234")
        viewModel.setRecoveryConfirm(true)
        viewModel.completeSetup()
        assertFalse(store.isSetup())
        assertEquals(0, store.setupCalls)
        assertEquals(1, keys.provisionCalls)
        assertFalse(viewModel.state.value.setupComplete)
        assertFalse(viewModel.state.value.unlocked)
    }

    @Test
    fun rotateRecoveryRollsBackWrapWhenHashStoreFails() {
        val store = FakeAppLock(failRotate = true)
        val keys = FakeLedgerKeys(sessionOpen = true)
        val viewModel = LockViewModel(store, dispatcher, { 1L }, keys)
        viewModel.unlockWithPinGiven("1234")
        viewModel.requestRotateRecovery()
        viewModel.setPin("1234")
        viewModel.confirmSensitiveAction()
        assertEquals(1, keys.rollbackWrapCalls)
        assertTrue(viewModel.state.value.error!!.contains("rotate"))
    }

    @Test
    fun recoverRewrapsPin() {
        val keys = FakeLedgerKeys()
        var opened = 0
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L }, keys, openLedger = { opened += 1 })
        viewModel.showRecover(true)
        viewModel.setRecoveryTyped(FakeAppLock.INITIAL_RECOVERY)
        viewModel.setPinConfirm("5678")
        viewModel.recoverAndResetPin()
        assertTrue(viewModel.state.value.unlocked)
        assertEquals("5678", keys.lastRewrapPin)
        assertEquals(1, opened)
    }

    @Test
    fun unlockClearsWorkingFlag() {
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L })
        viewModel.unlockWithPinGiven("1234")
        assertFalse(viewModel.state.value.working)
    }

    @Test
    fun biometricUnwrapOpensColdSession() {
        val keys = FakeLedgerKeys()
        var opened = 0
        val viewModel = LockViewModel(FakeAppLock(), dispatcher, { 1L }, keys, openLedger = { opened += 1 })
        viewModel.unlockWithUnwrappedKey(ByteArray(32) { 1 })
        assertTrue(viewModel.state.value.unlocked)
        assertTrue(keys.sessionOpen)
        assertEquals(1, opened)
        assertFalse(viewModel.state.value.pinFallback)
    }

    @Test
    fun wrapUpgradeRollsBackWhenRecoveryHashFails() {
        val store = FakeAppLock(failRotate = true)
        val keys = FakeLedgerKeys(upgrade = true)
        val viewModel = LockViewModel(store, dispatcher, { 1L }, keys)
        viewModel.setPin("1234")
        viewModel.unlockWithPin()
        assertTrue(viewModel.state.value.wrapUpgradeRequired)
        viewModel.setUpgradeRecoveryConfirm(true)
        viewModel.completeWrapUpgrade()
        assertEquals(1, keys.finishMigrationCalls)
        assertEquals(1, keys.rollbackWrapCalls)
        assertTrue(viewModel.state.value.error!!.contains("recovery"))
        assertTrue(viewModel.state.value.wrapUpgradeRequired)
        assertFalse(viewModel.state.value.unlocked)
    }

    @Test
    fun recoverRollsBackWrapWhenPinResetFails() {
        val store = FakeAppLock(failReset = true)
        val keys = FakeLedgerKeys()
        val viewModel = LockViewModel(store, dispatcher, { 1L }, keys)
        viewModel.showRecover(true)
        viewModel.setRecoveryTyped(FakeAppLock.INITIAL_RECOVERY)
        viewModel.setPinConfirm("5678")
        viewModel.recoverAndResetPin()
        assertEquals("5678", keys.lastRewrapPin)
        assertEquals(1, keys.rollbackWrapCalls)
        assertFalse(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.error!!.contains("PIN"))
    }

    @Test
    fun wrapUpgradeClearsAfterSessionEviction() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        val keys = FakeLedgerKeys(upgrade = true)
        var evicted = 0
        val viewModel = lockingViewModel(keys) { evicted += 1 }
        viewModel.setPin("1234")
        viewModel.unlockWithPin()
        assertTrue(viewModel.state.value.wrapUpgradeRequired)
        viewModel.onAppBackgrounded()
        main.scheduler.advanceTimeBy(1_000)
        main.scheduler.runCurrent()
        assertEquals(1, evicted)
        assertFalse(viewModel.state.value.wrapUpgradeRequired)
        assertTrue(viewModel.state.value.sessionEvicted)
        assertFalse(viewModel.state.value.unlocked)
    }

    @Test
    fun biometricUnwrapCopiesKeyBeforeCallerZerosIt() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        val keys = FakeLedgerKeys()
        var opened = 0
        val viewModel = LockViewModel(FakeAppLock(), main, { 1L }, keys, openLedger = { opened += 1 })
        val key = ByteArray(32) { 7 }
        viewModel.unlockWithUnwrappedKey(key)
        key.fill(0)
        main.scheduler.advanceUntilIdle()
        assertTrue(keys.capturedUnwrapped.contentEquals(ByteArray(32) { 7 }))
        assertTrue(viewModel.state.value.unlocked)
        assertEquals(1, opened)
    }

    @Test
    fun setupWithBiometricCheckboxWaitsForSeal() {
        val store = FakeAppLock(setup = false)
        val viewModel = LockViewModel(store, dispatcher, { 1L }, FakeLedgerKeys())
        viewModel.setPin("1234")
        viewModel.setPinConfirm("1234")
        viewModel.setRecoveryConfirm(true)
        viewModel.setBiometric(true)
        viewModel.completeSetup()
        assertTrue(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.pendingBiometricSeal)
        assertFalse(store.biometricEnabled())
        viewModel.cancelBiometricSeal()
        assertFalse(viewModel.state.value.pendingBiometricSeal)
        assertFalse(viewModel.state.value.biometric)
        assertFalse(store.biometricEnabled())
    }

    @Test
    fun backgroundLocksImmediatelyAndEvictsAfterGrace() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        val keys = FakeLedgerKeys()
        var evicted = 0
        val viewModel =
            LockViewModel(
                FakeAppLock(),
                dispatcher,
                { 1L },
                keys,
                {},
                {
                    evicted += 1
                    keys.evictSession()
                },
                1_000,
            )
        viewModel.unlockWithPinGiven("1234")
        viewModel.onAppBackgrounded()
        assertFalse(viewModel.state.value.unlocked)
        assertTrue(keys.sessionOpen)
        assertEquals(0, evicted)
        main.scheduler.advanceTimeBy(999)
        main.scheduler.runCurrent()
        assertEquals(0, evicted)
        main.scheduler.advanceTimeBy(1)
        main.scheduler.runCurrent()
        assertEquals(1, evicted)
        assertTrue(viewModel.state.value.sessionEvicted)
        assertFalse(keys.sessionOpen)
    }

    @Test
    fun backgroundDuringBiometricPromptStillEvictsAfterGrace() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        val keys = FakeLedgerKeys()
        var evicted = 0
        val viewModel = lockingViewModel(keys) { evicted += 1 }
        viewModel.unlockWithPinGiven("1234")
        viewModel.setBiometricPromptActive(true)
        viewModel.onAppBackgrounded()
        assertTrue(viewModel.state.value.unlocked)
        assertTrue(keys.sessionOpen)
        main.scheduler.advanceTimeBy(1_000)
        main.scheduler.runCurrent()
        assertEquals(1, evicted)
        assertFalse(keys.sessionOpen)
        assertFalse(viewModel.state.value.unlocked)
        assertTrue(viewModel.state.value.sessionEvicted)
    }

    @Test
    fun backgroundDuringFilePickerStillEvictsAfterGrace() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        val keys = FakeLedgerKeys()
        var evicted = 0
        val viewModel = lockingViewModel(keys) { evicted += 1 }
        viewModel.unlockWithPinGiven("1234")
        viewModel.setExternalUiActive(true)
        viewModel.onAppBackgrounded()
        assertTrue(viewModel.state.value.unlocked)
        main.scheduler.advanceTimeBy(1_000)
        main.scheduler.runCurrent()
        assertEquals(1, evicted)
        assertFalse(viewModel.state.value.unlocked)
        assertFalse(keys.sessionOpen)
    }

    @Test
    fun foregroundBeforeGraceKeepsTheSession() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        val keys = FakeLedgerKeys()
        var evicted = 0
        val viewModel =
            LockViewModel(
                FakeAppLock(),
                dispatcher,
                { 1L },
                keys,
                {},
                {
                    evicted += 1
                    keys.evictSession()
                },
                1_000,
            )
        viewModel.unlockWithPinGiven("1234")
        viewModel.onAppBackgrounded()
        main.scheduler.advanceTimeBy(500)
        main.scheduler.runCurrent()
        viewModel.onAppForegrounded()
        main.scheduler.advanceTimeBy(5_000)
        main.scheduler.runCurrent()
        assertEquals(0, evicted)
        assertFalse(viewModel.state.value.sessionEvicted)
        assertTrue(keys.sessionOpen)
        assertFalse(viewModel.state.value.unlocked)
    }

    private fun lockingViewModel(keys: FakeLedgerKeys, onEvict: () -> Unit): LockViewModel = LockViewModel(
        FakeAppLock(),
        dispatcher,
        { 1L },
        keys,
        {},
        {
            onEvict()
            keys.evictSession()
        },
        1_000,
    )

    private fun LockViewModel.unlockWithPinGiven(pin: String) {
        setPin(pin)
        unlockWithPin()
        assertTrue(state.value.unlocked)
    }
}

private class FakeAppLock(
    private var setup: Boolean = true,
    private val failRotate: Boolean = false,
    private val failSetup: Boolean = false,
    private val failReset: Boolean = false,
) : AppLockRepository {
    var pin: String = "1234"
    var recovery: String = INITIAL_RECOVERY
    var setupCalls: Int = 0
    private var biometric = false
    private var attempts = 0
    private var lockoutUntil = 0L

    override fun isSetup(): Boolean = setup

    override fun biometricEnabled(): Boolean = biometric

    override fun setBiometricEnabled(enabled: Boolean): Boolean {
        biometric = enabled
        return true
    }

    override fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean {
        setupCalls += 1
        if (failSetup || !AppLockCrypto.pinOk(pin)) return false
        this.pin = pin
        recovery = AppLockCrypto.normalizeRecovery(recoveryCode)
        this.biometric = biometric
        setup = true
        clearUnlockFailures()
        return true
    }

    override fun verifyPin(pin: String): Boolean = pin == this.pin

    override fun verifyRecovery(code: String): Boolean = AppLockCrypto.normalizeRecovery(code) == recovery

    override fun resetPin(newPin: String): Boolean {
        if (failReset || !AppLockCrypto.pinOk(newPin)) return false
        pin = newPin
        clearUnlockFailures()
        return true
    }

    override fun rotateRecovery(newCode: String): Boolean {
        if (failRotate) return false
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

private class FakeLedgerKeys(
    var sessionOpen: Boolean = false,
    var upgrade: Boolean = false,
    var provisionOk: Boolean = true,
) : LedgerKeySession {
    var provisionCalls: Int = 0
    var finishMigrationCalls: Int = 0
    var rollbackWrapCalls: Int = 0
    var discardCalls: Int = 0
    var evictCalls: Int = 0
    var lastRewrapPin: String? = null
    var lastRewrapRecovery: String? = null
    var capturedUnwrapped: ByteArray? = null
    private var sessionKey: ByteArray? = if (sessionOpen) ByteArray(32) else null
    private var biometricWrap: ByteArray? = null

    override fun isSessionOpen(): Boolean = sessionOpen

    override fun sessionKeyOrNull(): ByteArray? = sessionKey?.copyOf()

    override fun needsWrapUpgrade(): Boolean = upgrade

    override fun provision(pin: String, recovery: String): Boolean {
        provisionCalls += 1
        if (!provisionOk) return false
        sessionOpen = true
        sessionKey = ByteArray(32)
        upgrade = false
        return true
    }

    override fun unlockWithPin(pin: String): Boolean {
        sessionOpen = true
        sessionKey = ByteArray(32)
        return true
    }

    override fun unlockWithRecovery(recovery: String): Boolean {
        sessionOpen = true
        sessionKey = ByteArray(32)
        return true
    }

    override fun unlockWithUnwrappedKey(key: ByteArray): Boolean {
        capturedUnwrapped = key.copyOf()
        sessionOpen = true
        sessionKey = key.copyOf()
        return true
    }

    override fun biometricWrapBlob(): ByteArray? = biometricWrap

    override fun persistBiometricWrap(blob: ByteArray?): Boolean {
        biometricWrap = blob
        return true
    }

    override fun rewrapPin(newPin: String): Boolean {
        lastRewrapPin = newPin
        return true
    }

    override fun rewrapRecovery(newRecovery: String): Boolean {
        lastRewrapRecovery = newRecovery
        return true
    }

    override fun finishLegacyMigration(pin: String, recovery: String): Boolean {
        finishMigrationCalls += 1
        upgrade = false
        sessionOpen = true
        sessionKey = ByteArray(32)
        return true
    }

    override fun evictSession() {
        evictCalls += 1
        sessionOpen = false
        sessionKey?.fill(0)
        sessionKey = null
    }

    override fun discardOrphanWraps(): Boolean {
        discardCalls += 1
        if (sessionOpen) return true
        return true
    }

    override fun rollbackLastWrap(): Boolean {
        rollbackWrapCalls += 1
        return true
    }
}
