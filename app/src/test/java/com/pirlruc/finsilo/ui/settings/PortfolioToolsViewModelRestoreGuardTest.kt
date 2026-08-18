package com.pirlruc.finsilo.ui.settings

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.security.AppLockRepository
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioToolsViewModelRestoreGuardTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: FinsiloDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        database =
            Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun restoreWithoutRecoveryIsRefused() {
        val viewModel = PortfolioToolsViewModel(RoomPortfolioRepository(database), FakeRecoveryLock())
        viewModel.setRecovery("WRONG-CODE-0000-0000")
        viewModel.restoreBackup(ByteArray(32))
        assertFalse(viewModel.state.value.confirmRestore)
        assertTrue(viewModel.state.value.error!!.contains("recovery"))
        assertNull(viewModel.state.value.status)
    }
}

private class FakeRecoveryLock : AppLockRepository {
    override fun isSetup(): Boolean = true

    override fun biometricEnabled(): Boolean = false

    override fun setBiometricEnabled(enabled: Boolean) = Unit

    override fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean = true

    override fun verifyPin(pin: String): Boolean = pin == "1234"

    override fun verifyRecovery(code: String): Boolean = AppLockCrypto.normalizeRecovery(code) == "ABCD1234EFGH5678"

    override fun resetPin(newPin: String): Boolean = true

    override fun rotateRecovery(newCode: String): Boolean = true

    override fun failedUnlockAttempts(): Int = 0

    override fun pinLockoutUntilMs(): Long = 0

    override fun recordFailedUnlock(nowMs: Long) = Unit

    override fun clearUnlockFailures() = Unit
}
