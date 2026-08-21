package com.pirlruc.finsilo.ui.settings

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.security.AppLockRepository
import com.pirlruc.finsilo.domain.backup.LedgerBackupCodec
import com.pirlruc.finsilo.domain.backup.LedgerBackupExtras
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: FinsiloDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        database =
            Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun restoreWithEmptyPassphraseIsRefused() {
        val viewModel = toolsViewModel()
        viewModel.restoreBackup(ByteArray(32))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.confirmRestore)
        assertTrue(viewModel.state.value.error!!.contains("backup recovery"))
        assertNull(viewModel.state.value.status)
    }

    @Test
    fun restoreDoesNotRequireCurrentLockRecovery() {
        val viewModel = toolsViewModel()
        val extras =
            LedgerBackupExtras(
                watchlist = WatchlistSnapshot(
                    items = listOf(WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)),
                ),
            )
        val backupPassphrase = "ZZZZ9999YYYY8888"
        val bytes = LedgerBackupCodec.encrypt(SamplePortfolioFactory.create(), backupPassphrase, extras)
        assertFalse(FakeRecoveryLock().verifyRecovery(backupPassphrase))
        viewModel.setRecovery(backupPassphrase)
        viewModel.restoreBackup(bytes)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.confirmRestore)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun truncatedBackupIsRefusedEvenWhenLockRecoveryMatches() {
        val viewModel = toolsViewModel()
        viewModel.setRecovery("ABCD1234EFGH5678")
        viewModel.restoreBackup(ByteArray(32))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.confirmRestore)
        assertTrue(viewModel.state.value.error!!.contains("truncated"))
    }

    @Test
    fun exportStillRequiresCurrentLockRecovery() {
        val viewModel = toolsViewModel()
        viewModel.setRecovery("ZZZZ9999YYYY8888")
        viewModel.prepareBackupExport("backup.fsi")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.pendingExport)
        assertTrue(viewModel.state.value.error!!.contains("current recovery"))
    }

    @Test
    fun exportQueuesBytesUntilPickerWrites() {
        val repository = RoomPortfolioRepository(database)
        kotlinx.coroutines.runBlocking { repository.write(SamplePortfolioFactory.create()) }
        val viewModel = toolsViewModel(repository)
        viewModel.setRecovery("ABCD1234EFGH5678")
        viewModel.prepareBackupExport("backup.fsi")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.pendingExport != null)
        assertEquals("backup.fsi", viewModel.state.value.pendingExportName)
        assertNull(viewModel.state.value.status)
        viewModel.onExportConsumed(written = true)
        assertNull(viewModel.state.value.pendingExport)
        assertTrue(viewModel.state.value.status!!.contains("written"))
    }

    private fun toolsViewModel(
        repository: RoomPortfolioRepository = RoomPortfolioRepository(database),
    ): PortfolioToolsViewModel = PortfolioToolsViewModel(repository, FakeRecoveryLock(), cryptoDispatcher = dispatcher)
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
