package com.pirlruc.finsilo.ui.ledger

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import com.pirlruc.finsilo.domain.usecase.RecordManualQuoteUseCase
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LedgerSaveActionsTest {
    private lateinit var database: FinsiloDatabase
    private lateinit var repository: RoomPortfolioRepository
    private val record = RecordLedgerEntryUseCase(newId = { "id-${nextId++}" })
    private val manual = RecordManualQuoteUseCase()

    @Before
    fun setUp() {
        nextId = 1
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomPortfolioRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveEntryRejectsABlankFormAndAUseCaseRefusal() = runTest {
        val blank = LedgerSaveActions.saveEntry(repository, record, repository.load(), LedgerUiState(quantity = "", unitPriceNative = ""))
        assertTrue(blank is LedgerSaveOutcome.StateOnly)
        assertEquals("Fill date, quantity, and price with valid numbers.", (blank as LedgerSaveOutcome.StateOnly).state.error)

        val refused = LedgerSaveActions.saveEntry(
            repository,
            record,
            repository.load(),
            buyState(quantity = "0", price = "10"),
        )
        assertTrue((refused as LedgerSaveOutcome.StateOnly).state.error != null)
        assertEquals(false, refused.state.saving)
    }

    @Test
    fun fundedBuyPostsTheDepositThenALaterBuyReusesTheFxRate() = runTest {
        val first = LedgerSaveActions.saveEntry(repository, record, repository.load(), buyState(symbol = "VWCE", currency = Currency.EUR))
        val posted = first as LedgerSaveOutcome.Posted
        assertTrue(posted.status.contains("cash deposit"))

        val second = LedgerSaveActions.saveEntry(
            repository,
            record,
            repository.load(),
            buyState(symbol = "AAPL", currency = Currency.USD, fx = "0.92", price = "1"),
        )
        assertTrue(second is LedgerSaveOutcome.Posted)
        assertTrue((second as LedgerSaveOutcome.Posted).status.startsWith("Saved buy"))
        assertTrue(repository.load().fxRates.isNotEmpty())
    }

    @Test
    fun saveEntryReportsAClosedDatabase() = runTest {
        database.close()
        val outcome = LedgerSaveActions.saveEntry(repository, record, emptyBook(), buyState())
        val state = (outcome as LedgerSaveOutcome.StateOnly).state
        assertTrue(state.error != null)
        assertEquals(false, state.saving)
    }

    @Test
    fun manualClosePersistsALocalInstrumentAndRejectsAListedOne() = runTest {
        val ct = Asset("ct", "CT1", "Certificados", AssetType.CT, Currency.EUR)
        repository.saveLedgerEntry(ct, cashLike(ct.id), null)
        val book = repository.load()
        val saved = LedgerSaveActions.saveManualClose(
            repository,
            manual,
            book,
            LedgerUiState(date = "2026-08-16", manualClose = "12.5", existingAssetId = ct.id),
        )
        val manualSaved = saved as LedgerSaveOutcome.ManualSaved
        assertEquals("Saved statement close (not a transaction).", manualSaved.state.status)
        assertEquals("", manualSaved.state.manualClose)
        assertTrue(manualSaved.snapshot.marketData.any { it.assetId == ct.id })

        val stock = Asset("stk", "AAA", "Listed", AssetType.STOCK, Currency.EUR)
        repository.saveLedgerEntry(stock, cashLike(stock.id), null)
        val rejected = LedgerSaveActions.saveManualClose(
            repository,
            manual,
            repository.load(),
            LedgerUiState(date = "2026-08-16", manualClose = "1", existingAssetId = stock.id),
        )
        assertTrue((rejected as LedgerSaveOutcome.StateOnly).state.error!!.contains("GET-only"))

        val missing = LedgerSaveActions.saveManualClose(
            repository,
            manual,
            repository.load(),
            LedgerUiState(manualClose = "", existingAssetId = null),
        )
        assertTrue((missing as LedgerSaveOutcome.StateOnly).state.error!!.contains("statement close"))
    }

    @Test
    fun manualCloseReportsAClosedDatabase() = runTest {
        val ct = Asset("ct", "CT1", "Certificados", AssetType.CT, Currency.EUR)
        repository.saveLedgerEntry(ct, cashLike(ct.id), null)
        val book = repository.load()
        database.close()
        val outcome = LedgerSaveActions.saveManualClose(
            repository,
            manual,
            book,
            LedgerUiState(date = "2026-08-16", manualClose = "3", existingAssetId = ct.id),
        )
        assertNull((outcome as LedgerSaveOutcome.StateOnly).state.status)
        assertTrue(outcome.state.error != null)
    }

    private fun buyState(
        symbol: String = "VWCE",
        currency: Currency = Currency.EUR,
        fx: String = "",
        quantity: String = "1",
        price: String = "10",
        existing: String? = null,
        newInstrument: Boolean = true,
    ) = LedgerUiState(
        loading = false,
        date = "2026-08-16",
        quantity = quantity,
        unitPriceNative = price,
        feesEur = "0",
        eurPerUsd = fx,
        existingAssetId = existing,
        newInstrument = newInstrument,
        symbol = symbol,
        name = symbol,
        assetType = AssetType.ETF,
        currency = currency,
    )

    private fun emptyBook() = com.pirlruc.finsilo.domain.model.PortfolioSnapshot(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    private fun cashLike(assetId: String) = Transaction(
        id = "tx-$assetId",
        assetId = assetId,
        date = LocalDate.of(2026, 8, 1),
        type = TransactionType.DEPOSIT_CASH,
        quantity = BigDecimal.TEN,
        unitPriceNative = BigDecimal.ONE,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = BigDecimal.ONE,
        feesEur = BigDecimal.ZERO,
    )

    companion object {
        private var nextId: Int = 1
    }
}
