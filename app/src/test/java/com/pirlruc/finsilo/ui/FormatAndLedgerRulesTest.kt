package com.pirlruc.finsilo.ui

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.ui.ledger.LedgerFormMapper
import com.pirlruc.finsilo.ui.ledger.LedgerQuoteGate
import com.pirlruc.finsilo.ui.ledger.LedgerTemplateActions
import com.pirlruc.finsilo.ui.ledger.LedgerUiState
import com.pirlruc.finsilo.ui.ledger.label
import com.pirlruc.finsilo.ui.theme.chartColor
import com.pirlruc.finsilo.ui.theme.label
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndLedgerRulesTest {
    @Test
    fun formatsEveryCurrencyAndSignedAmounts() {
        val amount = BigDecimal("12.5")
        assertTrue(formatEur(amount).contains("12,50") || formatEur(amount).contains("12.50"))
        assertTrue(formatEur(12.5).isNotBlank())
        assertTrue(formatPercent(BigDecimal("1.2")).endsWith("%"))
        Currency.entries.forEach { currency ->
            assertTrue(formatNative(amount, currency).isNotBlank())
        }
        assertTrue(formatSignedEur(BigDecimal.ONE).startsWith("+"))
        assertTrue(formatSignedEur(BigDecimal.ONE.negate()).startsWith("-"))
        assertFalse(formatSignedEur(BigDecimal.ZERO).startsWith("+"))
        assertFalse(formatSignedEur(BigDecimal.ZERO).startsWith("-"))
    }

    @Test
    fun themeLabelsAndChartColorsCoverEveryAssetType() {
        AssetType.entries.forEach { type ->
            assertTrue(type.label().isNotBlank())
            assertNotNull(type.chartColor())
        }
    }

    @Test
    fun transactionLabelsAndFormMappingCoverEachKind() {
        TransactionType.entries.forEach { type ->
            assertTrue(type.label().isNotBlank())
        }
        val cash = asset("cash", "EUR-CASH", AssetType.CASH)
        val stock = asset("stk", "SYN", AssetType.STOCK, Currency.USD)
        val crypto = asset("cry", "BTC", AssetType.CRYPTO)
        val deposit = asset("dep", "TERM", AssetType.DEPOSIT)
        val assets = listOf(cash, stock, crypto, deposit)
        val buyNew =
            LedgerUiState(
                type = TransactionType.BUY,
                date = "2026-01-15",
                quantity = "2",
                unitPriceNative = "10",
                feesEur = "1",
                eurPerUsd = "0.9",
                newInstrument = true,
                symbol = "SYN2",
                name = "Synthetic",
                assetType = AssetType.ETF,
                currency = Currency.USD,
                isin = "",
                quoteSymbol = "",
                assets = assets,
            )
        val request = LedgerFormMapper.toRequest(buyNew)
        assertEquals(TransactionType.BUY, request?.type)
        assertEquals("SYN2", request?.newAsset?.symbol)
        assertNull(request?.newAsset?.isin)
        assertNull(LedgerFormMapper.toRequest(buyNew.copy(date = "nope")))
        assertNull(LedgerFormMapper.toRequest(buyNew.copy(quantity = "")))
        assertNull(LedgerFormMapper.toRequest(buyNew.copy(unitPriceNative = "x")))
        val interest = buyNew.copy(type = TransactionType.INTEREST, newInstrument = false, existingAssetId = deposit.id, quantity = "")
        assertEquals(BigDecimal.ONE, LedgerFormMapper.toRequest(interest)?.quantity)
        val withdrawal = buyNew.copy(type = TransactionType.WITHDRAWAL, newInstrument = false, existingAssetId = cash.id, unitPriceNative = "")
        assertEquals(BigDecimal.ONE, LedgerFormMapper.toRequest(withdrawal)?.unitPriceNative)
        assertEquals(BigDecimal.ZERO, LedgerFormMapper.toRequest(withdrawal)?.feesEur)
        assertTrue(LedgerFormMapper.selectableAssets(interest).all { it.assetType.allowsInterest })
        assertTrue(LedgerFormMapper.selectableAssets(buyNew.copy(type = TransactionType.DIVIDEND)).all { it.assetType.allowsDividend })
        assertEquals(assets, LedgerFormMapper.selectableAssets(buyNew.copy(type = TransactionType.SELL)))
        assertTrue(LedgerFormMapper.selectableAssets(buyNew.copy(type = TransactionType.DEPOSIT_CASH)).isEmpty())
        assertEquals(stock, LedgerFormMapper.selectedAsset(buyNew.copy(existingAssetId = stock.id, newInstrument = false)))
        assertEquals(Currency.USD, LedgerFormMapper.selectedCurrency(buyNew))
        assertEquals(Currency.USD, LedgerFormMapper.selectedCurrency(buyNew.copy(newInstrument = false, existingAssetId = stock.id)))
        assertEquals(Currency.EUR, LedgerFormMapper.selectedCurrency(buyNew.copy(newInstrument = false, existingAssetId = null)))
        assertEquals(AssetType.ETF, LedgerFormMapper.selectedType(buyNew))
        assertEquals(AssetType.CRYPTO, LedgerFormMapper.selectedType(buyNew.copy(newInstrument = false, existingAssetId = crypto.id)))
        assertTrue(LedgerFormMapper.needsFxField(buyNew))
        assertTrue(LedgerFormMapper.needsFxField(buyNew.copy(currency = Currency.EUR, assetType = AssetType.CRYPTO)))
        assertTrue(LedgerFormMapper.needsFxField(buyNew.copy(newInstrument = false, existingAssetId = stock.id, currency = Currency.EUR)))
        assertFalse(LedgerFormMapper.needsFxField(buyNew.copy(currency = Currency.EUR, assetType = AssetType.ETF, newInstrument = false)))
        assertTrue(LedgerFormMapper.isLocallyValuedSelection(buyNew.copy(assetType = AssetType.CT)))
        assertTrue(LedgerFormMapper.isLocallyValuedSelection(buyNew.copy(newInstrument = false, existingAssetId = deposit.id)))
        assertFalse(LedgerFormMapper.isLocallyValuedSelection(buyNew.copy(assetType = AssetType.ETF)))
        val withIsin = LedgerFormMapper.toRequest(buyNew.copy(isin = "XS0000000000", quoteSymbol = "SYN2.DE", feesEur = "bad"))
        assertEquals("XS0000000000", withIsin?.newAsset?.isin)
        assertEquals("SYN2.DE", withIsin?.newAsset?.quoteSymbol)
        assertEquals(BigDecimal.ZERO, withIsin?.feesEur)
    }

    @Test
    fun quoteGateAndTemplatesCoverMissingAndPresentInstruments() {
        val blank = LedgerUiState(type = TransactionType.BUY, newInstrument = true, symbol = "  ")
        assertNull(LedgerQuoteGate.probeAsset(blank))
        assertNull(LedgerQuoteGate.probeAsset(blank.copy(type = TransactionType.SELL, symbol = "SYN")))
        assertNull(LedgerQuoteGate.probeAsset(blank.copy(symbol = "TERM", assetType = AssetType.DEPOSIT)))
        val probed = LedgerQuoteGate.probeAsset(
            blank.copy(symbol = " SYN ", name = "", assetType = AssetType.STOCK, isin = "  ", quoteSymbol = "SYN.DE"),
        )
        assertEquals("SYN", probed?.symbol)
        assertEquals("SYN", probed?.name)
        assertEquals("SYN.DE", probed?.quoteSymbol)
        val snapshot = PortfolioSnapshot(listOf(asset("stk", "SYN", AssetType.STOCK)), emptyList(), emptyList(), emptyList(), emptyList())
        val present = LedgerTemplate("t1", "Buy synth", TransactionType.BUY, assetId = "stk", quantity = "1", unitPriceNative = "2")
        val applied = LedgerTemplateActions.apply(LedgerUiState(), snapshot, present)
        assertEquals("stk", applied.existingAssetId)
        assertFalse(applied.newInstrument)
        val missing = LedgerTemplateActions.apply(LedgerUiState(), snapshot, present.copy(assetId = "gone", label = "Old"))
        assertNull(missing.existingAssetId)
        assertTrue(missing.newInstrument)
        assertTrue(missing.status!!.contains("no longer"))
        val draft = LedgerTemplateActions.draft(LedgerUiState(type = TransactionType.INTEREST, quantity = "1"), "  ")
        assertEquals("INTEREST", draft.label)
    }

    private fun asset(id: String, symbol: String, type: AssetType, currency: Currency = Currency.EUR) = Asset(id, symbol, "Synthetic $symbol", type, currency)
}
