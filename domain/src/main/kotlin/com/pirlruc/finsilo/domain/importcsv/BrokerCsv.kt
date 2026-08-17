package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Recognised broker export layout. */
enum class BrokerCsvFormat {
    TRADING_212,
    DEGIRO_TRANSACTIONS,
    DEGIRO_ACCOUNT,
    REVOLUT_STOCKS,
}

/** One normalised row from a broker CSV. [type] is null when the row is skipped. */
data class BrokerCsvLine(
    val date: LocalDate?,
    val type: TransactionType?,
    val skipReason: String?,
    val symbol: String,
    val name: String,
    val isin: String?,
    val quoteSymbol: String?,
    val assetType: AssetType,
    val quantity: BigDecimal,
    val unitPriceNative: BigDecimal,
    val currency: Currency,
    val feesEur: BigDecimal,
    val eurPerUsd: BigDecimal?,
    val externalId: String?,
    val sourceLine: Int,
    val format: BrokerCsvFormat,
)

/** Parse outcome for a single CSV document. */
data class BrokerCsvParseResult(val format: BrokerCsvFormat?, val lines: List<BrokerCsvLine>, val error: String? = null)

/** Detect and parse Trading 212, DEGIRO, and Revolut statement CSVs. */
object BrokerCsv {
    /** Parse one document. Unrecognised headers return [BrokerCsvParseResult.error]. */
    fun parse(text: String): BrokerCsvParseResult {
        val records = CsvReader.records(text)
        val start = headerIndex(records) ?: return BrokerCsvParseResult(null, emptyList(), "Not a Trading 212, DEGIRO, or Revolut CSV.")
        val headers = records[start]
        val format =
            BrokerCsvDetect.from(headers) ?: return BrokerCsvParseResult(null, emptyList(), "Not a Trading 212, DEGIRO, or Revolut CSV.")
        val table = CsvTable(headers, records.drop(start + 1))
        return BrokerCsvParseResult(format, parseLines(format, table))
    }

    /** Parse several files (DEGIRO transactions + account statement) into one list. */
    fun parseAll(texts: List<String>): BrokerCsvParseResult {
        if (texts.isEmpty()) return BrokerCsvParseResult(null, emptyList(), "Choose at least one CSV file.")
        val parsed = texts.map { parse(it) }
        val failed = parsed.filter { it.error != null }
        if (failed.size == parsed.size) {
            return BrokerCsvParseResult(null, emptyList(), failed.first().error)
        }
        val ok = parsed.filter { it.error == null && it.format != null }
        val dropAccountTrades = ok.any { it.format == BrokerCsvFormat.DEGIRO_TRANSACTIONS }
        val lines =
            ok.flatMap { result ->
                if (dropAccountTrades && result.format == BrokerCsvFormat.DEGIRO_ACCOUNT) {
                    result.lines.filter { it.type != TransactionType.BUY && it.type != TransactionType.SELL }
                } else {
                    result.lines
                }
            }
        return BrokerCsvParseResult(ok.first().format, lines)
    }

    private fun parseLines(format: BrokerCsvFormat, table: CsvTable): List<BrokerCsvLine> = when (format) {
        BrokerCsvFormat.TRADING_212 -> Trading212CsvParser.parse(table)
        BrokerCsvFormat.DEGIRO_TRANSACTIONS -> DegiroTransactionsParser.parse(table)
        BrokerCsvFormat.DEGIRO_ACCOUNT -> DegiroAccountParser.parse(table)
        BrokerCsvFormat.REVOLUT_STOCKS -> RevolutStocksParser.parse(table)
    }

    private fun headerIndex(records: List<List<String>>): Int? {
        val limit = minOf(10, records.size)
        for (index in 0 until limit) {
            if (BrokerCsvDetect.from(records[index]) != null) return index
        }
        return null
    }
}
