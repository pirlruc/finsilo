package com.pirlruc.finsilo.domain

import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerMoney
import com.pirlruc.finsilo.domain.importcsv.MoneyParts
import com.pirlruc.finsilo.domain.lock.PinLockoutPolicy
import com.pirlruc.finsilo.domain.market.CoinGeckoParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.AlertPublishKey
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveAccuracyTest {
    @Test
    fun currenciesConvertAndFallBack() {
        val rate = BigDecimal("0.90")
        Currency.entries.forEach { currency ->
            assertTrue(MoneyMath.toEur(BigDecimal.TEN, currency, rate).signum() > 0)
        }
        val ledger = PositionLedger()
        val early = CurrencyRate(LocalDate.of(2024, 1, 1), BigDecimal("0.91"), mapOf(Currency.GBP to BigDecimal("1.15")))
        assertNull(ledger.eurPerUnitOn(LocalDate.of(2024, 6, 1), emptyList(), Currency.USD))
        assertEquals(0, BigDecimal.ONE.compareTo(ledger.eurPerUnitOn(LocalDate.of(2024, 6, 1), emptyList(), Currency.EUR)))
        assertNull(ledger.eurPerUnitOn(LocalDate.of(2023, 1, 1), listOf(early), Currency.GBP))
        assertNotNull(ledger.eurPerUnitOn(LocalDate.of(2024, 6, 1), listOf(early), Currency.GBX))
        assertNotNull(ledger.eurPerUnitOn(LocalDate.of(2024, 6, 1), listOf(early), Currency.USD))
    }

    @Test
    fun listingSuffixesAndFrankfurter() {
        listOf("SHEL.L", "VOD.LON", "AZN.UK", "NOVO.CO", "NESN.SW", "NOVN.VX", "ERIC.ST", "EQNR.OL", "SAP.DE", "MC.PA", "ASML.AS", "ENI.MI", "IBE.MC", "ABI.BR", "EDP.LS", "NOKIA.HE", "RYA.IR", "OMV.VI", "ANDR.AT", "AAPL").forEach { symbol ->
            assertNotNull(QuoteCurrency.listingCurrency(symbol))
        }
        val stock = Asset("a", "SHEL.L", "Shell", AssetType.STOCK, Currency.EUR)
        val cash = Asset("c", "EUR", "Cash", AssetType.CASH, Currency.EUR)
        val crypto = Asset("k", "BTC", "Bitcoin", AssetType.CRYPTO, Currency.EUR)
        assertEquals(Currency.GBP, QuoteCurrency.of(stock))
        assertEquals(Currency.EUR, QuoteCurrency.of(cash))
        assertEquals(Currency.USD, QuoteCurrency.of(crypto))
        assertTrue(QuoteCurrency.needsUsdFx(crypto))
        assertEquals(0, BigDecimal("0.92").compareTo(FrankfurterParser.eurPerUsd("""{"rates":{"EUR":0.92}}""")))
        assertEquals(1, FrankfurterParser.latestCrosses("""{"rates":{"GBP":1.15,"ZZZ":1}}""").size)
        assertEquals(2, FrankfurterParser.eurPerUsdSeries("""{"rates":{"2026-01-01":{"GBP":1.1},"2026-01-02":{"USD":0.9}}}""").size)
        assertTrue(FrankfurterParser.eurPerUsdSeries("not-json").isEmpty())
        assertTrue(CoinGeckoParser.dailyCloses("""{"prices":[[1],[1755302400000,1.5]],"total_volumes":[[1,2]]}""").isNotEmpty())
        assertTrue(CoinGeckoParser.dailyCloses("nope").isEmpty())
    }

    @Test
    fun brokerMoneyAndCommodityRows() {
        listOf("GBP", "CHF", "DKK", "SEK", "NOK").forEach { code ->
            val booked = BrokerMoney.book(MoneyParts("2", "10", code, "20", "EUR", "1.1", "0", "EUR"))
            assertEquals(Currency.fromCode(code), booked?.currency)
        }
        val gbx = BrokerMoney.book(MoneyParts("2", "200", "GBX", "", "", "0.90", "1", "GBX"))
        assertEquals(Currency.GBX, gbx?.currency)
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.TEN, "GBP", "1.1"))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal("100"), "GBX", "1.1"))
        assertNull(BrokerMoney.toEurCash(BigDecimal.TEN, "XXX", "1.1"))
        val csv =
            """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            Exchange,Current,not-a-date,not-a-date,Exchanged to XAU,1,0,XAU,COMPLETED,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Spent 10 EUR,1,0.5,XAU,COMPLETED,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Exchanged,1,0,XAG,PENDING,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Cash,1,0,EUR,COMPLETED,1
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertTrue(parsed.lines.any { it.skipReason != null })
        assertTrue(parsed.lines.any { it.symbol == "XAU" && it.unitPriceNative.signum() > 0 })
    }

    @Test
    fun lockoutAndAlertKeyBound() {
        val sameBoot = PinLockoutPolicy.remainingMs(1_000, 5_000, 100, 9_000, 3, 3)
        val otherBoot = PinLockoutPolicy.remainingMs(1_000, 2_000, 100, 9_000, 4, 3)
        val unset = PinLockoutPolicy.remainingMs(1_000, 2_000, 100, 9_000, 4, -1)
        assertTrue(sameBoot >= otherBoot)
        assertEquals(1_000L, unset)
        val bounded = AlertPublishKey.bound(setOf("a", "b"), "c", max = 2)
        assertEquals(2, bounded.size)
        assertTrue("c" in bounded)
    }

    @Test
    fun moneyAndFrankfurterEdges() {
        assertNull(BrokerMoney.book(MoneyParts("", "1", "EUR", "1", "EUR", "", "0", "EUR")))
        assertNull(BrokerMoney.book(MoneyParts("1", "", "GBP", "", "", "", "0", "EUR")))
        assertNull(BrokerMoney.book(MoneyParts("2", "", "USD", "184", "EUR", "", "1", "USD")))
        assertNotNull(BrokerMoney.book(MoneyParts("2", "", "USD", "184", "EUR", "0.92", "1", "USD")))
        assertNotNull(BrokerMoney.book(MoneyParts("2", "10", "USD", "20", "USD", "1.20", "1", "USD")))
        assertNotNull(BrokerMoney.book(MoneyParts("2", "10", "GBP", "", "", "1.20", "1", "GBP")))
        assertNotNull(BrokerMoney.book(MoneyParts("2", "2500", "GBX", "280", "EUR", "", "0", "EUR")))
        assertNull(BrokerMoney.eurPerUsdRate("0"))
        assertNotNull(BrokerMoney.eurPerUsdRate("1.20"))
        assertNotNull(BrokerMoney.eurPerUsdRate("2"))
        assertNotNull(BrokerMoney.eurPerUsdRate("-0.5"))
        assertEquals("GBX", BrokerMoney.currencyCode("Price (GBX)", "EUR"))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "", ""))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "CHF", "0.95"))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "DKK", "0.13"))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "SEK", "0.09"))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "NOK", "0.08"))
        assertTrue(FrankfurterParser.latestCrosses("[]").isEmpty())
        assertTrue(FrankfurterParser.eurPerUsdSeries("""{"2026-02-01":{"EUR":"nope"}}""").isEmpty())
        assertEquals(1, FrankfurterParser.eurPerUsdSeries("""{"2026-02-01":{"EUR":1.0,"USD":0.9}}""").size)
        assertNull(FrankfurterParser.eurPerUsd("""{"amount":"x"}"""))
        assertTrue(CoinGeckoParser.dailyCloses("{}").isEmpty())
        assertTrue(CoinGeckoParser.dailyCloses("""{"prices":[]}""").isEmpty())
    }
}
