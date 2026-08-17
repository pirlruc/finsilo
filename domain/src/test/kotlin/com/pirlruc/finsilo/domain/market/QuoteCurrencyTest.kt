package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuoteCurrencyTest {
    @Test
    fun cryptoAndCommodityFeedsStayUsdEvenWhenBookedInEur() {
        val btcEur = Asset("btc", "BTC", "Bitcoin", AssetType.CRYPTO, Currency.EUR)
        val goldEur = Asset("xau", "XAU", "Gold", AssetType.COMMODITY, Currency.EUR)
        assertEquals(Currency.USD, QuoteCurrency.of(btcEur))
        assertEquals(Currency.USD, QuoteCurrency.of(goldEur))
        assertTrue(QuoteCurrency.needsUsdFx(btcEur))
        assertTrue(QuoteCurrency.needsUsdFx(goldEur))
    }

    @Test
    fun europeanListingsStayEurAndUsNamesStayUsd() {
        val etf = Asset("vwce", "VWCE.DE", "All-World", AssetType.ETF, Currency.EUR)
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        assertEquals(Currency.EUR, QuoteCurrency.of(etf))
        assertFalse(QuoteCurrency.needsUsdFx(etf))
        assertEquals(Currency.USD, QuoteCurrency.of(apple))
        assertTrue(QuoteCurrency.needsUsdFx(apple))
    }

    @Test
    fun locallyValuedAndCashFollowBookingCurrency() {
        val ct = Asset("ct", "CT", "CT", AssetType.CT, Currency.EUR)
        val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
        assertEquals(Currency.EUR, QuoteCurrency.of(ct))
        assertFalse(QuoteCurrency.needsUsdFx(ct))
        assertEquals(Currency.EUR, QuoteCurrency.of(cash))
        assertFalse(QuoteCurrency.needsUsdFx(cash))
    }
}
