package com.pirlruc.finsilo.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.security.AppLockStore
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class LockViewModel(private val store: AppLockStore) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState(setupComplete = store.isSetup(), biometric = store.biometricEnabled()))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        if (!_state.value.setupComplete) {
            _state.update { it.copy(recoveryCode = AppLockCrypto.generateRecoveryCode()) }
        }
    }

    fun setPin(value: String) = _state.update { it.copy(pin = value.filter { ch -> ch.isDigit() }.take(8), error = null) }

    fun setPinConfirm(value: String) = _state.update { it.copy(pinConfirm = value.filter { ch -> ch.isDigit() }.take(8), error = null) }

    fun setRecoveryTyped(value: String) = _state.update { it.copy(pin = value, error = null) }

    fun setRecoveryConfirm(value: Boolean) = _state.update { it.copy(recoveryConfirm = value) }

    fun setBiometric(value: Boolean) = _state.update { it.copy(biometric = value) }

    fun setBiometricAvailable(value: Boolean) = _state.update { it.copy(biometricAvailable = value) }

    fun showRecover(value: Boolean) = _state.update { it.copy(recovering = value, pin = "", error = null) }

    fun setError(message: String?) = _state.update { it.copy(error = message) }

    fun completeSetup() {
        viewModelScope.launch { saveSetup() }
    }

    fun unlockWithPin() {
        val pin = _state.value.pin
        if (!store.verifyPin(pin)) {
            _state.update { it.copy(error = "Incorrect PIN.") }
            return
        }
        _state.update { it.copy(unlocked = true, pin = "", error = null) }
    }

    fun unlockWithBiometric() {
        _state.update { it.copy(unlocked = true, error = null) }
    }

    fun recoverAndResetPin() {
        viewModelScope.launch { applyRecovery() }
    }

    fun rotateRecovery() {
        val code = AppLockCrypto.generateRecoveryCode()
        if (!store.rotateRecovery(code)) {
            _state.update { it.copy(error = "Could not rotate recovery code.") }
            return
        }
        _state.update { it.copy(newRecoveryCode = code, status = "Save this new recovery code. The previous code no longer works.") }
    }

    fun clearNewRecovery() = _state.update { it.copy(newRecoveryCode = null) }

    fun persistBiometric(enabled: Boolean) {
        store.setBiometricEnabled(enabled)
        _state.update { it.copy(biometric = enabled) }
    }

    private fun saveSetup() {
        val current = _state.value
        val error = setupError(current)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(setupComplete = true, unlocked = true, pin = "", pinConfirm = "", error = null) }
    }

    private fun setupError(current: LockUiState): String? {
        if (!AppLockCrypto.pinOk(current.pin) || current.pin != current.pinConfirm) {
            return if (!AppLockCrypto.pinOk(current.pin)) "PIN must be 4–8 digits." else "PIN confirmation does not match."
        }
        if (!current.recoveryConfirm) return "Confirm that you saved the recovery code."
        if (!store.setup(current.pin, current.recoveryCode, current.biometric)) return "Could not store the lock."
        return null
    }

    private fun applyRecovery() {
        val current = _state.value
        val error = recoveryError(current)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(unlocked = true, recovering = false, pin = "", pinConfirm = "", error = null) }
    }

    private fun recoveryError(current: LockUiState): String? {
        if (!store.verifyRecovery(current.pin)) return "Recovery code does not match."
        if (!AppLockCrypto.pinOk(current.pinConfirm)) return "Choose a new 4–8 digit PIN."
        if (!store.resetPin(current.pinConfirm)) return "Could not reset PIN."
        return null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LockViewModel(container.lockStore) as T
        }
    }
}
