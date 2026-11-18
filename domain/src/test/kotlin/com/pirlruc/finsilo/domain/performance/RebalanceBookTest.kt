package com.pirlruc.finsilo.domain.performance

import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.HoldingValuation
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RebalanceBookTest {
    @Test
    fun emptyHoldingsKeepTheTypeKeys() {
        val slice = slice(AssetType.ETF, "60", "50")
        val open = slice(AssetType.STOCK, "40", null)
        val (values, targets) = RebalanceBook.valuesAndTargets(emptyList(), listOf(slice, open))
        assertEquals(0, BigDecimal("60").compareTo(values.getValue("ETF")))
        assertEquals(0, BigDecimal("50").compareTo(targets.getValue("ETF")))
        assertTrue("STOCK" !in targets)
    }

    @Test
    fun aTypeTargetSplitsAcrossItsHoldings() {
        val etf = holding("VWCE", AssetType.ETF, "75")
        val other = holding("IWDA", AssetType.ETF, "25")
        val cash = holding("CASH", AssetType.CASH, "0")
        val (values, targets) = RebalanceBook.valuesAndTargets(
            listOf(etf, other, cash),
            listOf(slice(AssetType.ETF, "100", "80"), slice(AssetType.CASH, "0", "20")),
        )
        assertEquals(0, BigDecimal("75").compareTo(values.getValue("VWCE")))
        assertEquals(0, BigDecimal("60").compareTo(targets.getValue("VWCE")))
        assertEquals(0, BigDecimal("20").compareTo(targets.getValue("IWDA")))
        assertTrue("CASH" !in targets)
        val untouched = RebalanceBook.valuesAndTargets(listOf(etf), listOf(slice(AssetType.COMMODITY, "0", "10")))
        assertTrue(untouched.second.isEmpty())
        assertTrue(RebalanceBook.valuesAndTargets(listOf(etf), emptyList()).second.isEmpty())
    }

    private fun holding(symbol: String, type: AssetType, value: String) = HoldingValuation(
        asset = Asset(symbol, symbol, symbol, type, Currency.EUR),
        quantity = BigDecimal.ONE,
        priceEur = BigDecimal(value),
        valueEur = BigDecimal(value),
        costEur = BigDecimal(value),
        unrealizedPnlEur = BigDecimal.ZERO,
    )

    private fun slice(type: AssetType, value: String, target: String?) = AllocationSlice(
        assetType = type,
        valueEur = BigDecimal(value),
        weightPercent = BigDecimal(value),
        targetPercent = target?.let { BigDecimal(it) },
        driftPercent = null,
    )
}
