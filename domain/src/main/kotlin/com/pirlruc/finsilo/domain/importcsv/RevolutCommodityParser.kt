package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.time.LocalDate

/**
 * Revolut commodity pocket statement. Amount is the metal quantity.
 * The file has no EUR cash leg, so the buy is booked at a zero EUR price.
 */
internal object RevolutCommodityParser {
    private val metals = setOf("XAU", "XAG", "XPT", "XPD")

    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date = BrokerDates.parse(row.get("Started Date", "Date started", "Date"))
        val symbol = metal(row)
        val reason = reject(date, row, symbol)
        if (reason != null) return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, reason, date)
        return buy(sourceLine, date!!, symbol, row)
    }

    private fun reject(date: LocalDate?, row: CsvRow, symbol: String): String? = when {
        date == null -> "Unreadable date"
        incomplete(row) -> "Ignored ${row.get("State")}"
        symbol.isBlank() -> "Commodity row missing metal"
        BrokerMoney.parseAmount(row.get("Amount")) == null -> "Commodity row missing amount"
        else -> null
    }

    private fun incomplete(row: CsvRow): Boolean {
        val state = row.get("State")
        return state.isNotBlank() && !state.equals("COMPLETED", ignoreCase = true)
    }

    private fun buy(sourceLine: Int, date: LocalDate, symbol: String, row: CsvRow): BrokerCsvLine {
        val eur = eurNotional(row)
        val booked = BrokerMoney.book(
            MoneyParts(
                row.get("Amount"),
                if (eur == null) "0" else "",
                "EUR",
                eur?.toPlainString() ?: "0",
                "EUR",
                "",
                row.get("Fee"),
                "EUR",
            ),
        )
        return StatementRows.bookedHolding(
            ListedTrade(
                format = BrokerCsvFormat.REVOLUT_STOCKS,
                sourceLine = sourceLine,
                date = date,
                type = TransactionType.BUY,
                ticker = symbol,
                isin = null,
                name = symbol,
                quote = symbol,
                booked = booked,
                missingInstrument = "Commodity row missing metal",
                missingBook = "Commodity row missing amount",
            ),
        )
    }

    /** EUR spent when the description names it. The metal row then books that cash leg as the price. */
    private fun eurNotional(row: CsvRow): java.math.BigDecimal? {
        val described = Regex("(?i)(\\d+[\\d.,]*)\\s*EUR").find(row.get("Description")) ?: return null
        return BrokerMoney.parseAmount(described.groupValues[1])
    }

    private fun metal(row: CsvRow): String {
        val currency = row.get("Currency").filter { it.isLetter() }.uppercase()
        if (currency in metals) return currency
        val described = Regex("(?i)\\b(XAU|XAG|XPT|XPD)\\b").find(row.get("Description"))
        return described?.value?.uppercase().orEmpty()
    }
}
