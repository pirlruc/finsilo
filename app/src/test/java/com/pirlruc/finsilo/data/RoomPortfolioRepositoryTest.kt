package com.pirlruc.finsilo.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoomPortfolioRepositoryTest {
    private lateinit var database: FinsiloDatabase
    private lateinit var repository: RoomPortfolioRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database =
            Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = RoomPortfolioRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveLedgerEntryPersistsCashAndRebuildsNavHistory() = runTest {
        val asOf = LocalDate.of(2026, 8, 16)
        val cash =
            Asset(
                id = "asset-cash",
                symbol = "EUR-CASH",
                name = "Euro cash",
                assetType = AssetType.CASH,
                baseCurrency = Currency.EUR,
            )
        val deposit =
            Transaction(
                id = "tx-deposit",
                assetId = cash.id,
                date = asOf,
                type = TransactionType.DEPOSIT_CASH,
                quantity = BigDecimal("1000"),
                unitPriceNative = BigDecimal.ONE,
                exchangeRateAtExecution = BigDecimal.ONE,
                unitPriceEur = BigDecimal.ONE,
                feesEur = BigDecimal.ZERO,
            )
        repository.saveLedgerEntry(cash, deposit, null)
        val loaded = repository.load()
        assertEquals(1, loaded.assets.size)
        assertEquals(1, loaded.transactions.size)
        assertEquals(0, BigDecimal("1000").compareTo(loaded.transactions.single().quantity))
        repository.rebuildNavHistoryIfNeeded(loaded, asOf)
        val nav = repository.loadNavHistory()
        assertTrue(nav.isNotEmpty())
        assertEquals(0, BigDecimal("1000").compareTo(nav.last().valueEur))
        assertTrue(!nav.last().date.isBefore(asOf))
    }
}
