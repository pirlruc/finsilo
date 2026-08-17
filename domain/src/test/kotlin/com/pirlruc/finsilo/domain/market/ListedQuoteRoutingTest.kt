package com.pirlruc.finsilo.domain.market

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListedQuoteRoutingTest {
    @Test
    fun europeanSuffixesAreStooqFirstAndMapped() {
        assertTrue(ListedQuoteRouting.looksEuropean("VWCE.DE"))
        assertEquals("vwce.de", ListedQuoteRouting.stooqTicker("VWCE.DE"))
        assertTrue(ListedQuoteRouting.looksEuropean("MC.PA"))
        assertEquals("mc.pa", ListedQuoteRouting.stooqTicker("MC.PA"))
        assertTrue(ListedQuoteRouting.looksEuropean("ASML.AS"))
        assertEquals("asml.as", ListedQuoteRouting.stooqTicker("ASML.AS"))
        assertTrue(ListedQuoteRouting.looksEuropean("UCG.MI"))
        assertEquals("ucg.mi", ListedQuoteRouting.stooqTicker("UCG.MI"))
        assertTrue(ListedQuoteRouting.looksEuropean("SAN.MC"))
        assertEquals("san.mc", ListedQuoteRouting.stooqTicker("SAN.MC"))
        assertTrue(ListedQuoteRouting.looksEuropean("SHEL.L"))
        assertEquals("shel.uk", ListedQuoteRouting.stooqTicker("SHEL.L"))
        assertTrue(ListedQuoteRouting.looksEuropean("HSBA.UK"))
        assertEquals("hsba.uk", ListedQuoteRouting.stooqTicker("HSBA.UK"))
        assertTrue(ListedQuoteRouting.looksEuropean("NESN.SW"))
        assertEquals("nesn.sw", ListedQuoteRouting.stooqTicker("NESN.SW"))
        assertTrue(ListedQuoteRouting.looksEuropean("NOVN.VX"))
        assertEquals("novn.sw", ListedQuoteRouting.stooqTicker("NOVN.VX"))
        assertTrue(ListedQuoteRouting.looksEuropean("GALP.LS"))
        assertEquals("galp.ls", ListedQuoteRouting.stooqTicker("GALP.LS"))
        assertTrue(ListedQuoteRouting.looksEuropean("PTYAAAA00001"))
    }

    @Test
    fun usNamesStayAvFirst() {
        assertFalse(ListedQuoteRouting.looksEuropean("AAPL"))
        assertEquals("aapl.us", ListedQuoteRouting.stooqTicker("AAPL"))
        assertFalse(ListedQuoteRouting.looksEuropean("AAPL.US"))
        assertEquals("aapl.us", ListedQuoteRouting.stooqTicker("AAPL.US"))
        assertEquals("AAPL", ListedQuoteRouting.avSymbol("AAPL.US"))
        assertEquals("SHEL", ListedQuoteRouting.avSymbol("SHEL.L"))
    }
}
