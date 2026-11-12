package com.pirlruc.finsilo.domain.importcsv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerCsvDetectBranchTest {
    @Test
    fun headerAliasStopsAtALetterAndMatchesABoundary() {
        assertFalse(headerHasAlias("total", "t"))
        assertTrue(headerHasAlias("t.", "t"))
        assertTrue(headerHasAlias("price per share", "price"))
        assertFalse(headerHasAlias("total", "to"))
        assertTrue(headerHasAlias("to", "to"))
    }

    @Test
    fun eachBrokerHeaderCombinationIsRecognised() {
        assertEquals(
            BrokerCsvFormat.TRADING_212,
            BrokerCsvDetect.from(listOf("Action", "Ticker")),
        )
        assertEquals(
            BrokerCsvFormat.TRADING_212,
            BrokerCsvDetect.from(listOf("Action", "No. of shares")),
        )
        assertNull(BrokerCsvDetect.from(listOf("Action")))

        assertEquals(
            BrokerCsvFormat.REVOLUT_STOCKS,
            BrokerCsvDetect.from(listOf("Ticker", "Type", "Preco por acao")),
        )
        assertNull(BrokerCsvDetect.from(listOf("Symbol", "Type", "Action", "Price")))

        val commodity = listOf("Type", "Product", "Amount", "Currency", "Started date")
        assertTrue(BrokerCsvDetect.isCommodity(commodity))
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(commodity))
        assertFalse(BrokerCsvDetect.isCommodity(listOf("Type", "Product", "Montant", "Divisa")))
        assertFalse(BrokerCsvDetect.isCommodity(listOf("Type", "Product", "Amount", "Moeda", "Ticker")))

        val crypto = listOf("Symbol", "Type", "Quantity", "Price", "Value", "Fees", "Date")
        assertTrue(BrokerCsvDetect.isCrypto(crypto))
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(crypto))
        assertFalse(BrokerCsvDetect.isCrypto(crypto + "Action"))
        assertFalse(BrokerCsvDetect.isCrypto(listOf("Symbol", "Type", "Quantity", "Price", "Value", "Fee", "Date") - "Price"))

        assertEquals(
            BrokerCsvFormat.DEGIRO_TRANSACTIONS,
            BrokerCsvDetect.from(listOf("Product", "Quantity", "Value")),
        )
        assertEquals(
            BrokerCsvFormat.DEGIRO_TRANSACTIONS,
            BrokerCsvDetect.from(listOf("Product", "Quantity", "Price")),
        )
        assertNull(BrokerCsvDetect.from(listOf("Product", "Quantity")))
        assertNull(BrokerCsvDetect.from(listOf("Action", "Product", "Quantity", "Price")))

        assertEquals(
            BrokerCsvFormat.DEGIRO_ACCOUNT,
            BrokerCsvDetect.from(listOf("Description", "Balance")),
        )
        assertEquals(
            BrokerCsvFormat.DEGIRO_ACCOUNT,
            BrokerCsvDetect.from(listOf("Description", "Change")),
        )
        val split = MutableList(12) { "x" }
        split[8] = ""
        split[10] = ""
        split[0] = "Description"
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(split))
        split[8] = "filled"
        assertNull(BrokerCsvDetect.from(split))
        split[8] = ""
        split[10] = "filled"
        assertNull(BrokerCsvDetect.from(split))
        assertNull(BrokerCsvDetect.from(listOf("Symbol", "Type")))
        assertNull(BrokerCsvDetect.from(listOf("Product", "Amount", "Currency", "Started date")))
        assertNull(BrokerCsvDetect.from(listOf("Type", "Product", "Currency", "Started date")))
        assertFalse(
            BrokerCsvDetect.isCrypto(listOf("Symbol", "Type", "Quantity", "Price", "Fees", "Date")),
        )
        assertNull(BrokerCsvDetect.from(listOf("Description", "Balance", "Quantity")))
    }
}
