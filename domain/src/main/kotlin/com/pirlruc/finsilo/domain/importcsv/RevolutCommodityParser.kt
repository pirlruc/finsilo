package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.time.LocalDate

/**
 * Revolut commodity pocket statement. Amount is the metal quantity.
 * A buy is booked only when the row or a same-day EUR cash leg names an EUR notional.
 */
internal object RevolutCommodityParser {
    private val metals = setOf("XAU", "XAG", "XPT", "XPD")

    fun parse(table: CsvTable): List<BrokerCsvLine> {
        val peers = table.rows.filter { cells -> cells.any { it.isNotBlank() } }.map { CsvRow(table.headers, it) }
        return table.mapRows { line, row -> parseRow(line, row, peers) }
    }

    private fun parseRow(sourceLine: Int, row: CsvRow, peers: List<CsvRow>): BrokerCsvLine {
        val date = BrokerDates.parse(row.get("Started Date", "Date started", "Date"))
        val symbol = metal(row)
        val reason = reject(date, row, symbol)
        if (reason != null) return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, reason, date)
        return buy(sourceLine, date!!, symbol, row, peers)
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

    private fun buy(sourceLine: Int, date: LocalDate, symbol: String, row: CsvRow, peers: List<CsvRow>): BrokerCsvLine {
        val eur = eurNotional(row) ?: pairedEur(row, peers)
        if (eur == null || eur.signum() <= 0) {
            return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Commodity buy has no EUR amount", date)
        }
        val booked = BrokerMoney.book(
            MoneyParts(
                row.get("Amount"),
                "",
                "EUR",
                eur.toPlainString(),
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

    /** Same-day EUR cash row, preferring a description that names this metal. */
    private fun pairedEur(row: CsvRow, peers: List<CsvRow>): java.math.BigDecimal? {
        val date = row.get("Started Date", "Date started", "Date")
        val symbol = metal(row)
        val matches = peers.mapNotNull { other -> eurLeg(other, date, symbol) }
        return matches.firstOrNull { it.second }?.first ?: matches.firstOrNull()?.first
    }

    private fun eurLeg(other: CsvRow, date: String, symbol: String): Pair<java.math.BigDecimal, Boolean>? {
        val sameDay = other.get("Started Date", "Date started", "Date") == date
        val eur = other.get("Currency").filter { it.isLetter() }.uppercase() == "EUR"
        val amount = if (sameDay && eur) BrokerMoney.absAmount(other.get("Amount")) else null
        if (amount == null || amount.signum() <= 0) return null
        val named = symbol.isNotBlank() && other.get("Description").contains(symbol, ignoreCase = true)
        return amount to named
    }

    private fun metal(row: CsvRow): String {
        val currency = row.get("Currency").filter { it.isLetter() }.uppercase()
        if (currency in metals) return currency
        if (currency == "EUR" || currency == "USD" || currency == "GBP") return ""
        val described = Regex("(?i)\\b(XAU|XAG|XPT|XPD)\\b").find(row.get("Description"))
        return described?.value?.uppercase().orEmpty()
    }
}
