package com.pirlruc.finsilo.domain.importcsv

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/** Counts local broker CSV rows. Skips when FINSILO_REPORTS_DIR is unset. */
class LocalReportCsvCountTest {
    @Test
    fun parseCountsOnly() {
        val raw = System.getenv("FINSILO_REPORTS_DIR")
        assumeTrue(raw != null && raw.isNotBlank())
        val dir = File(raw)
        assumeTrue(dir.isDirectory)
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".csv") }?.sortedBy { it.name }.orEmpty()
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val parsed = BrokerCsv.parse(file.readText())
            val kept = parsed.lines.count { it.skipReason == null }
            println("${file.name} parsed=${parsed.lines.size} kept=$kept")
        }
    }
}
