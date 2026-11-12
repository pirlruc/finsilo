package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.Currency
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerMoneyBranchTest {
    @Test
    fun booksEurUsdAndForeignQuotes() {
        val eur = BrokerMoney.book(parts(priceCurrency = "EUR"))
        assertEquals(Currency.EUR, eur?.currency)
        assertEquals(0, BigDecimal("10").compareTo(eur?.unitPriceNative))

        val fromPrefix = BrokerMoney.book(parts(price = "EUR 10", priceCurrency = ""))
        assertEquals(Currency.EUR, fromPrefix?.currency)

        val fromTotal = BrokerMoney.book(parts(price = "", total = "20", totalCurrency = "EUR"))
        assertEquals(0, BigDecimal("10").compareTo(fromTotal?.unitPriceNative))

        assertNull(BrokerMoney.book(parts(quantity = "0")))
        assertNull(BrokerMoney.book(parts(quantity = "n/a")))
        assertNull(BrokerMoney.book(parts(price = "", total = "")))

        val usd = BrokerMoney.book(parts(priceCurrency = "USD", total = "18", totalCurrency = "EUR"))
        assertEquals(Currency.USD, usd?.currency)
        assertTrue(usd?.eurPerUsd != null)

        val usdRate = BrokerMoney.book(parts(priceCurrency = "USD", exchangeRate = "1.20"))
        assertEquals(Currency.USD, usdRate?.currency)

        val usdFromTotal = BrokerMoney.book(
            parts(price = "", priceCurrency = "USD", total = "20", totalCurrency = "EUR"),
        )
        assertEquals(Currency.EUR, usdFromTotal?.currency)

        listOf("GBP", "CHF", "DKK", "SEK", "NOK").forEach { code ->
            val booked = BrokerMoney.book(
                parts(priceCurrency = code, total = "22", totalCurrency = "EUR"),
            )
            assertEquals(Currency.fromCode(code), booked?.currency)
            assertNull(
                BrokerMoney.book(parts(priceCurrency = code, total = "", exchangeRate = "")),
            )
        }

        val gbxTotal = BrokerMoney.book(
            parts(price = "100", priceCurrency = "GBX", total = "2", totalCurrency = "EUR"),
        )
        assertEquals(Currency.EUR, gbxTotal?.currency)

        val gbxFx = BrokerMoney.book(
            parts(price = "100", priceCurrency = "GBX", totalCurrency = "USD", exchangeRate = "0.90"),
        )
        assertEquals(Currency.GBX, gbxFx?.currency)
        assertNull(BrokerMoney.book(parts(price = "", priceCurrency = "GBX", totalCurrency = "USD")))

        val other = BrokerMoney.book(
            parts(priceCurrency = "CAD", total = "20", totalCurrency = "EUR"),
        )
        assertEquals(Currency.EUR, other?.currency)
        assertNull(
            BrokerMoney.book(parts(priceCurrency = "CAD", total = "20", totalCurrency = "USD")),
        )
    }

    @Test
    fun convertsFeesAndCashAcrossCurrencies() {
        val withUsdFee = BrokerMoney.book(
            parts(priceCurrency = "USD", exchangeRate = "0.90", fees = "1", feesCurrency = "USD"),
        )
        assertTrue((withUsdFee?.feesEur ?: BigDecimal.ZERO) > BigDecimal.ZERO)

        val gbxFee = BrokerMoney.book(
            parts(priceCurrency = "GBX", exchangeRate = "0.90", fees = "100", feesCurrency = "GBX"),
        )
        assertTrue((gbxFee?.feesEur ?: BigDecimal.ZERO) > BigDecimal.ZERO)

        val eurFee = BrokerMoney.book(parts(fees = "1.50", feesCurrency = "EUR"))
        assertEquals(0, BigDecimal("1.50").compareTo(eurFee?.feesEur))

        val unknownFee = BrokerMoney.book(parts(fees = "3", feesCurrency = "JPY"))
        assertEquals(0, BigDecimal.ZERO.compareTo(unknownFee?.feesEur))
        val zeroUnknownFee = BrokerMoney.book(parts(fees = "0", feesCurrency = "JPY"))
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroUnknownFee?.feesEur))
        listOf("GBP", "CHF", "DKK", "SEK", "NOK").forEach { code ->
            val booked = BrokerMoney.book(
                parts(priceCurrency = "USD", exchangeRate = "0.90", fees = "1", feesCurrency = code),
            )
            assertTrue((booked?.feesEur ?: BigDecimal.ZERO) > BigDecimal.ZERO)
            assertTrue(BrokerMoney.toEurCash(BigDecimal.ONE, code, "0.90")!! > BigDecimal.ZERO)
            assertNull(BrokerMoney.toEurCash(BigDecimal.ONE, code, ""))
        }
        val usdFeeWithoutRate = BrokerMoney.book(parts(fees = "1", feesCurrency = "USD"))
        assertEquals(0, BigDecimal.ZERO.compareTo(usdFeeWithoutRate?.feesEur))
        val gbxFeeWithoutRate = BrokerMoney.book(parts(fees = "100", feesCurrency = "GBX"))
        assertEquals(0, BigDecimal.ZERO.compareTo(gbxFeeWithoutRate?.feesEur))
        assertEquals(0, BigDecimal.ZERO.compareTo(BrokerMoney.book(parts(fees = ""))?.feesEur))
        assertNull(BrokerMoney.toEurCash(BigDecimal("100"), "GBX", ""))
        assertNull(
            BrokerMoney.book(parts(price = "100", priceCurrency = "GBX", total = "", totalCurrency = "EUR")),
        )

        assertEquals(0, BigDecimal("10").compareTo(BrokerMoney.toEurCash(BigDecimal("10"), "EUR", "")))
        assertEquals(
            0,
            BigDecimal("9.0").compareTo(BrokerMoney.toEurCash(BigDecimal("10"), "USD", "0.90")),
        )
        assertNull(BrokerMoney.toEurCash(BigDecimal("10"), "GBP", ""))
        assertTrue(BrokerMoney.toEurCash(BigDecimal("100"), "GBX", "0.90")!! > BigDecimal.ZERO)
        assertNull(BrokerMoney.toEurCash(BigDecimal("10"), "JPY", "0.90"))
        assertNull(BrokerMoney.toEurCash(BigDecimal.ZERO, "JPY", "0.90"))
        assertEquals(0, BigDecimal("4").compareTo(BrokerMoney.toEurCash(BigDecimal("4"), "", "")))

        assertNull(BrokerMoney.eurPerUsdRate(""))
        assertNull(BrokerMoney.eurPerUsdRate("0"))
        assertTrue(BrokerMoney.eurPerUsdRate("1.20")!! < BigDecimal.ONE)
        assertEquals(0, BigDecimal("0.90").compareTo(BrokerMoney.eurPerUsdRate("0.90")))
        assertEquals(0, BigDecimal("2").compareTo(BrokerMoney.eurPerUsdRate("2")))

        assertEquals("GBX", BrokerMoney.currencyCode("Price (GBX)", ""))
        assertEquals("GBX", BrokerMoney.currencyCode("xx (GBP) and GBX", ""))
        assertEquals("USD", BrokerMoney.currencyCode("usd", ""))
        assertEquals("EU", BrokerMoney.currencyCode("EU", ""))
        assertEquals("USD", BrokerMoney.currencyPrefix("USD 10"))
        assertEquals("", BrokerMoney.currencyPrefix("€ 10"))
        assertEquals("1234,50", BrokerMoney.numericPart("1 234,50 EUR"))
        assertEquals("+10", BrokerMoney.numericPart("+10"))
        assertEquals(0, BigDecimal("5").compareTo(BrokerMoney.absAmount("-5")))
    }

    private fun parts(
        quantity: String = "2",
        price: String = "10",
        priceCurrency: String = "EUR",
        total: String = "",
        totalCurrency: String = "",
        exchangeRate: String = "",
        fees: String = "",
        feesCurrency: String = "",
    ) = MoneyParts(quantity, price, priceCurrency, total, totalCurrency, exchangeRate, fees, feesCurrency)
}
