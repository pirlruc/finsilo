package com.pirlruc.finsilo.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.security.AppLockRepository
import com.pirlruc.finsilo.data.security.LedgerKeySession
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PinLockoutPolicy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings change that must be confirmed with the current PIN. */
enum class SensitiveLockAction {
    ROTATE_RECOVERY,
    TOGGLE_BIOMETRIC,
}

data class LockUiState(
    val setupComplete: Boolean = false,
    val unlocked: Boolean = false,
    val pin: String = "",
    val pinConfirm: String = "",
    val recoveryCode: String = "",
    val recoveryConfirm: Boolean = false,
    val biometric: Boolean = false,
    val biometricAvailable: Boolean = false,
    val error: String? = null,
    val status: String? = null,
    val recovering: Boolean = false,
    val newRecoveryCode: String? = null,
    val pendingSensitiveAction: SensitiveLockAction? = null,
    val pendingBiometricEnabled: Boolean = false,
    val wrapUpgradeRequired: Boolean = false,
    val upgradeRecovery: String = "",
    val upgradeRecoveryConfirm: Boolean = false,
    val working: Boolean = false,
)

class LockViewModel(
    private val store: AppLockRepository,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val keys: LedgerKeySession? = null,
    private val openLedger: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState(setupComplete = store.isSetup(), biometric = store.biometricEnabled()))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    @Volatile
    private var biometricPromptActive: Boolean = false

    @Volatile
    private var externalUiDepth: Int = 0

    @Volatile
    private var pendingUpgradePin: String? = null

    private val inFlight = AtomicBoolean(false)

    init {
        if (!_state.value.setupComplete) {
            _state.update { it.copy(recoveryCode = AppLockCrypto.generateRecoveryCode()) }
        }
    }

    fun setPin(value: String) = _state.update { it.copy(pin = value.filter { ch -> ch.isDigit() }.take(8), error = null) }

    fun setPinConfirm(value: String) = _state.update { it.copy(pinConfirm = value.filter { ch -> ch.isDigit() }.take(8), error = null) }

    fun setRecoveryTyped(value: String) = _state.update { it.copy(pin = value, error = null) }

    fun setRecoveryConfirm(value: Boolean) = _state.update { it.copy(recoveryConfirm = value) }

    fun setUpgradeRecoveryConfirm(value: Boolean) = _state.update { it.copy(upgradeRecoveryConfirm = value, error = null) }

    fun setBiometric(value: Boolean) = _state.update { it.copy(biometric = value) }

    fun setBiometricAvailable(value: Boolean) = _state.update { it.copy(biometricAvailable = value) }

    fun showRecover(value: Boolean) = _state.update { it.copy(recovering = value, pin = "", error = null) }

    fun setError(message: String?) = _state.update { it.copy(error = message) }

    fun setBiometricPromptActive(active: Boolean) {
        biometricPromptActive = active
    }

    fun setExternalUiActive(active: Boolean) {
        if (active) {
            externalUiDepth += 1
        } else {
            externalUiDepth = (externalUiDepth - 1).coerceAtLeast(0)
        }
    }

    fun onAppBackgrounded() {
        if (biometricPromptActive || externalUiDepth > 0) return
        val current = _state.value
        if (!current.setupComplete || !current.unlocked) return
        _state.update {
            it.copy(unlocked = false, pin = "", pinConfirm = "", pendingSensitiveAction = null, error = null)
        }
    }

    fun completeSetup() {
        runLockAction { saveSetup() }
    }

    fun unlockWithPin() {
        runLockAction { tryUnlockWithPin() }
    }

    fun unlockWithBiometric() {
        if (keys != null && !keys.isSessionOpen()) {
            _state.update { it.copy(error = "Enter your PIN after the app restarts.") }
            return
        }
        runLockAction {
            store.clearUnlockFailures()
            openLedger()
            _state.update { it.copy(unlocked = true, error = null) }
        }
    }

    fun recoverAndResetPin() {
        runLockAction { applyRecovery() }
    }

    fun completeWrapUpgrade() {
        runLockAction { persistWrapUpgrade() }
    }

    fun requestRotateRecovery() {
        _state.update {
            it.copy(pendingSensitiveAction = SensitiveLockAction.ROTATE_RECOVERY, pin = "", error = null, status = null)
        }
    }

    fun requestToggleBiometric() {
        val target = !_state.value.biometric
        _state.update {
            it.copy(
                pendingSensitiveAction = SensitiveLockAction.TOGGLE_BIOMETRIC,
                pendingBiometricEnabled = target,
                pin = "",
                error = null,
                status = null,
            )
        }
    }

    fun cancelSensitiveAction() {
        _state.update { it.copy(pendingSensitiveAction = null, pin = "", error = null) }
    }

    fun confirmSensitiveAction() {
        runLockAction { applySensitiveAction() }
    }

    private fun runLockAction(block: () -> Unit) {
        if (!inFlight.compareAndSet(false, true)) return
        viewModelScope.launch(computation) {
            _state.update { it.copy(working = true, error = null) }
            try {
                block()
            } finally {
                inFlight.set(false)
                _state.update { it.copy(working = false) }
            }
        }
    }

    fun clearNewRecovery() = _state.update { it.copy(newRecoveryCode = null, status = null) }

    private fun tryUnlockWithPin() {
        val pin = _state.value.pin
        val error = authenticatePin(pin)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        if (!unwrapLedgerForPin(pin)) return
        _state.update {
            it.copy(unlocked = !it.wrapUpgradeRequired, pin = "", error = null)
        }
    }

    private fun unwrapLedgerForPin(pin: String): Boolean {
        val gate = keys ?: return true
        if (!gate.unlockWithPin(pin)) {
            _state.update { it.copy(error = "Could not unlock the ledger.") }
            return false
        }
        if (gate.needsWrapUpgrade()) {
            pendingUpgradePin = pin
            _state.update {
                it.copy(
                    wrapUpgradeRequired = true,
                    upgradeRecovery = AppLockCrypto.generateRecoveryCode(),
                    upgradeRecoveryConfirm = false,
                    pin = "",
                    error = null,
                )
            }
            return true
        }
        openLedger()
        return true
    }

    private fun saveSetup() {
        val current = _state.value
        val error = setupValidationError(current)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        if (!persistSetup(current)) return
        openLedger()
        _state.update { it.copy(setupComplete = true, unlocked = true, pin = "", pinConfirm = "", error = null) }
    }

    private fun setupValidationError(current: LockUiState): String? {
        if (!AppLockCrypto.pinOk(current.pin) || current.pin != current.pinConfirm) {
            return if (!AppLockCrypto.pinOk(current.pin)) "PIN must be 4–8 digits." else "PIN confirmation does not match."
        }
        if (!current.recoveryConfirm) return "Confirm that you saved the recovery code."
        return null
    }

    private fun persistSetup(current: LockUiState): Boolean {
        if (!store.setup(current.pin, current.recoveryCode, current.biometric)) {
            _state.update { it.copy(error = "Could not store the lock.") }
            return false
        }
        if (keys != null && !keys.provision(current.pin, current.recoveryCode)) {
            _state.update { it.copy(error = "Could not wrap the database key.") }
            return false
        }
        return true
    }

    private fun applyRecovery() {
        val current = _state.value
        val error = recoveryUnlockError(current) ?: recoveryKeyError(current) ?: resetPinError(current)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        store.clearUnlockFailures()
        openLedger()
        _state.update { it.copy(unlocked = true, recovering = false, pin = "", pinConfirm = "", error = null) }
    }

    private fun recoveryUnlockError(current: LockUiState): String? {
        lockoutError()?.let { return it }
        if (!store.verifyRecovery(current.pin)) {
            store.recordFailedUnlock(nowMs())
            return failedSecretMessage("Recovery code does not match.")
        }
        if (!AppLockCrypto.pinOk(current.pinConfirm)) return "Choose a new 4–8 digit PIN."
        return null
    }

    private fun resetPinError(current: LockUiState): String? {
        if (!store.resetPin(current.pinConfirm)) return "Could not reset PIN."
        return null
    }

    private fun recoveryKeyError(current: LockUiState): String? {
        val gate = keys ?: return null
        if (!gate.unlockWithRecovery(current.pin)) return "Could not unlock the ledger."
        return rewrapAfterRecovery(gate, current)
    }

    private fun rewrapAfterRecovery(gate: LedgerKeySession, current: LockUiState): String? {
        if (gate.needsWrapUpgrade()) {
            if (gate.finishLegacyMigration(current.pinConfirm, current.pin)) return null
            return "Could not wrap the database key."
        }
        if (gate.rewrapPin(current.pinConfirm)) return null
        return "Could not rewrap the database key."
    }

    private fun persistWrapUpgrade() {
        val error = wrapUpgradeError()
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        pendingUpgradePin = null
        openLedger()
        _state.update {
            it.copy(wrapUpgradeRequired = false, unlocked = true, upgradeRecoveryConfirm = false, error = null)
        }
    }

    private fun wrapUpgradeError(): String? {
        val pin = pendingUpgradePin
        val gate = keys
        val current = _state.value
        if (pin == null || gate == null) return "Unlock with your PIN to finish this upgrade."
        if (!current.upgradeRecoveryConfirm) return "Confirm that you saved the new recovery code."
        return persistMigratedWraps(gate, pin, current.upgradeRecovery)
    }

    private fun persistMigratedWraps(gate: LedgerKeySession, pin: String, recovery: String): String? {
        if (!gate.finishLegacyMigration(pin, recovery)) return "Could not wrap the database key."
        if (!store.rotateRecovery(recovery)) return "Could not store the new recovery code."
        return null
    }

    private fun applySensitiveAction() {
        val current = _state.value
        val action = current.pendingSensitiveAction ?: return
        val error = authenticatePin(current.pin)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        when (action) {
            SensitiveLockAction.ROTATE_RECOVERY -> rotateRecoveryAfterPin()
            SensitiveLockAction.TOGGLE_BIOMETRIC -> persistBiometricAfterPin(current.pendingBiometricEnabled)
        }
    }

    private fun rotateRecoveryAfterPin() {
        val code = AppLockCrypto.generateRecoveryCode()
        if (keys != null && !keys.rewrapRecovery(code)) {
            _state.update { it.copy(error = "Could not rewrap the database key.") }
            return
        }
        if (!store.rotateRecovery(code)) {
            _state.update { it.copy(error = "Could not rotate recovery code.") }
            return
        }
        _state.update {
            it.copy(
                pendingSensitiveAction = null,
                pin = "",
                error = null,
                newRecoveryCode = code,
                status = "Save this new recovery code. The previous code no longer works.",
            )
        }
    }

    private fun persistBiometricAfterPin(enabled: Boolean) {
        store.setBiometricEnabled(enabled)
        _state.update {
            it.copy(
                pendingSensitiveAction = null,
                pin = "",
                error = null,
                biometric = enabled,
                status = if (enabled) "Biometric unlock enabled." else "Biometric unlock disabled.",
            )
        }
    }

    private fun authenticatePin(pin: String): String? {
        lockoutError()?.let { return it }
        if (!store.verifyPin(pin)) {
            store.recordFailedUnlock(nowMs())
            return failedSecretMessage("Incorrect PIN.")
        }
        store.clearUnlockFailures()
        return null
    }

    private fun lockoutError(): String? {
        val remaining = PinLockoutPolicy.remainingMs(nowMs(), store.pinLockoutUntilMs())
        return if (remaining > 0L) lockoutMessage(remaining) else null
    }

    private fun failedSecretMessage(mismatch: String): String {
        val remaining = PinLockoutPolicy.remainingMs(nowMs(), store.pinLockoutUntilMs())
        return if (remaining > 0L) lockoutMessage(remaining) else mismatch
    }

    private fun lockoutMessage(remainingMs: Long): String {
        val seconds = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
        return "Too many attempts. Try again in $seconds second(s)."
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LockViewModel(
                store = container.lockStore,
                keys = container.keys,
                openLedger = container::openLedger,
            ) as T
        }
    }
}
