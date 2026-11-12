package com.pirlruc.finsilo.domain.importcsv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerParserBranchTest {
    @Test
    fun degiroAccountSkipsUnbookableCashAndTrades() {
        val headers = listOf("Date", "Description", "Change", "")
        val lines = DegiroAccountParser.parse(
            CsvTable(
                headers,
                listOf(
                    listOf("2024-01-02", "Deposit", "JPY", "10"),
                    listOf("2024-01-03", "Withdrawal", "EUR", "5"),
                    listOf("2024-01-04", "Flatex Interest", "EUR", "1"),
                    listOf("2024-01-05", "Buy 2 VWCE", "JPY", "20"),
                    listOf("2024-01-06", "Dividend", "EUR", "0"),
                ),
            ),
        )
        assertTrue(lines.any { it.skipReason?.contains("cannot be booked") == true })
        assertTrue(lines.any { it.skipReason?.contains("missing amount") == true })
        assertTrue(lines.any { it.type != null })
        assertEquals(5, lines.size)
    }

    @Test
    fun revolutCryptoCoversUnknownTypesAndNonEuroPrices() {
        val headers = listOf("Symbol", "Type", "Quantity", "Price", "Value", "Fees", "Date")
        val lines = RevolutCryptoParser.parse(
            CsvTable(
                headers,
                listOf(
                    listOf("BTC", "transfer", "1", "USD 10", "20", "0", "2024-01-02"),
                    listOf("BTC", "buy", "1", "USD 10", "20", "0", "2024-01-02"),
                    listOf("ETH", "venda", "1", "10", "EUR 20", "0", "2024-01-03"),
                    listOf("ETH", "stake", "1", "10", "20", "0", "2024-01-04"),
                ),
            ),
        )
        assertEquals(4, lines.size)
        assertNull(lines[0].type)
        assertEquals("BTC", lines[1].symbol)
    }

    @Test
    fun revolutCommodityCoversStateAmountAndDescription() {
        val headers = listOf("Started Date", "Type", "Product", "Amount", "Currency", "State", "Description", "Fee")
        val lines = RevolutCommodityParser.parse(
            CsvTable(
                headers,
                listOf(
                    listOf("2024-01-02", "Buy", "Gold", "abc", "XAU", "COMPLETED", "", "0"),
                    listOf("2024-01-03", "Buy", "Gold", "1", "XAU", "PENDING", "", "0"),
                    listOf("2024-01-04", "Buy", "Gold", "1", "", "COMPLETED", "bought XAU", "0"),
                    listOf("2024-01-05", "Buy", "Gold", "1", "XAU", "", "spent 25 EUR", "0"),
                    listOf("nope", "Buy", "Gold", "1", "XAU", "COMPLETED", "", "0"),
                ),
            ),
        )
        assertEquals(5, lines.size)
        assertTrue(lines[0].skipReason?.contains("amount") == true)
        assertTrue(lines[1].skipReason?.contains("PENDING") == true)
        assertEquals("XAU", lines[2].symbol)
        assertEquals("XAU", lines[3].symbol)
        assertTrue(lines[4].skipReason?.contains("date") == true)
    }
}
