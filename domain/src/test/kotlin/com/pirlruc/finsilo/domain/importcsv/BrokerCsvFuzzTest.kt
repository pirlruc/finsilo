package com.pirlruc.finsilo.domain.importcsv

import java.nio.charset.Charset
import java.util.Random
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

/**
 * Jazzer-style regression targets. Each broker parser sees a bounded, seeded input set.
 * ClusterFuzzLite calls [fuzzerTestOneInput] on the companion objects. This class stays on the
 * normal :domain:test run and does not start a libFuzzer campaign.
 */
class BrokerCsvFuzzTest {
    @Test
    fun trading212ParserStaysInsideTheSeed() = exercise(Trading212CsvFuzz::fuzzerTestOneInput, 212L)

    @Test
    fun degiroTransactionsParserStaysInsideTheSeed() = exercise(DegiroTransactionsFuzz::fuzzerTestOneInput, 2121L)

    @Test
    fun degiroAccountParserStaysInsideTheSeed() = exercise(DegiroAccountFuzz::fuzzerTestOneInput, 2122L)

    @Test
    fun revolutStocksParserStaysInsideTheSeed() = exercise(RevolutStocksFuzz::fuzzerTestOneInput, 2123L)

    private fun exercise(target: (ByteArray) -> Unit, seed: Long) {
        val iterations = System.getenv("FINSILO_FUZZ_ITERATIONS")?.toIntOrNull()?.coerceIn(1, 500) ?: REGRESSION
        val random = Random(seed)
        repeat(iterations) {
            val body = ByteArray(random.nextInt(180))
            random.nextBytes(body)
            assertDoesNotThrow { target(body) }
        }
    }

    private companion object {
        const val REGRESSION: Int = 32
    }
}

object Trading212CsvFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val header = "Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total)\n"
        BrokerCsv.parse(header + String(data, UTF_8))
        BrokerCsv.parse(String(data, UTF_8))
    }
}

object DegiroTransactionsFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val header = "Date,Product,ISIN,Quantity,Price,Local value,Value,FX,Transaction fees\n"
        BrokerCsv.parse(header + String(data, UTF_8))
        BrokerCsv.parse(String(data, UTF_8))
    }
}

object DegiroAccountFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val header = "Date,Product,ISIN,Description,Change\n"
        BrokerCsv.parse(header + String(data, UTF_8))
        BrokerCsv.parse(String(data, UTF_8))
    }
}

object RevolutStocksFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val header = "Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency\n"
        BrokerCsv.parse(header + String(data, UTF_8))
        BrokerCsv.parse(String(data, UTF_8))
    }
}

private val UTF_8: Charset = Charsets.UTF_8
