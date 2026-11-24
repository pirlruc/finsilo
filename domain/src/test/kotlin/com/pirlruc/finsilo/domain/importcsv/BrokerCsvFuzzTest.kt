package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.fundamentals.OpenBbFundamentals
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

    @Test
    fun revolutCryptoParserStaysInsideTheSeed() = exercise(RevolutCryptoFuzz::fuzzerTestOneInput, 2124L)

    @Test
    fun revolutCommodityParserStaysInsideTheSeed() = exercise(RevolutCommodityFuzz::fuzzerTestOneInput, 2125L)

    @Test
    fun ibkrAndOpenBbParsersStayInsideTheSeed() {
        exercise(IbkrTradesFuzz::fuzzerTestOneInput, 2126L)
        exercise(OpenBbFundamentalsFuzz::fuzzerTestOneInput, 2127L)
    }

    private fun exercise(target: (ByteArray) -> Unit, seed: Long) {
        val raw = System.getenv("FUZZ_ITERATIONS") ?: System.getenv("FINSILO_FUZZ_ITERATIONS")
        val iterations = raw?.toIntOrNull()?.coerceIn(1, 500) ?: REGRESSION
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

object RevolutCryptoFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val header = "Symbol,Type,Quantity,Price,Value,Fees,Date\n"
        val records = CsvReader.records(header + String(data, UTF_8))
        if (records.isNotEmpty()) RevolutCryptoParser.parse(CsvTable(records.first(), records.drop(1)))
        ReadOnlyBrokers.coinbaseFills(String(data, UTF_8))
    }
}

object IbkrTradesFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        ReadOnlyBrokers.ibkrTrades(String(data, UTF_8))
        ReadOnlyBrokers.ibkrTrades("<FlexStatement>" + String(data, UTF_8) + "</FlexStatement>")
    }
}

object OpenBbFundamentalsFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val text = String(data, UTF_8)
        OpenBbFundamentals.parseLenient(text, text)
        OpenBbFundamentals.metricsUrl(text, "AAPL")
    }
}

object RevolutCommodityFuzz {
    @JvmStatic
    fun fuzzerTestOneInput(data: ByteArray) {
        val header = "Started Date,Symbol,Type,Amount\n"
        val records = CsvReader.records(header + String(data, UTF_8))
        if (records.isNotEmpty()) RevolutCommodityParser.parse(CsvTable(records.first(), records.drop(1)))
        ReadOnlyBrokers.ibkrTrades(String(data, UTF_8))
        ReadOnlyBrokers.krakenLedgers(String(data, UTF_8))
    }
}

private val UTF_8: Charset = Charsets.UTF_8
