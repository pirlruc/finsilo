package com.pirlruc.finsilo.ui.importcsv

import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvResult
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerImportSummaryTest {
    @Test
    fun summaryIncludesSkipReasons() {
        val result =
            ImportBrokerCsvResult(
                accepted = 0,
                fundedDeposits = 0,
                duplicates = 1,
                skipped = listOf("Line 2: CSV USD does not match holding EUR."),
                snapshot = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            )
        val text = brokerImportSummary(result)
        assertTrue(text.contains("Skipped 1: Line 2"))
        assertTrue(text.contains("1 duplicate(s)"))
    }

    @Test
    fun decodeCsvStripsBom() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Action,Time".toByteArray()
        assertEquals("Action,Time", decodeCsv(withBom))
    }
}
