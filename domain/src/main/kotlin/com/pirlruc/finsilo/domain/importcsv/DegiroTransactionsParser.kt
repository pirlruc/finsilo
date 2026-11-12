package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

internal object DegiroTransactionsParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> {
        val corporate = corporateKeys(table)
        return table.mapRows { line, row -> parseRow(line, row, corporate) }
    }

    private fun parseRow(sourceLine: Int, row: CsvRow, corporate: Set<String>): BrokerCsvLine {
        val date = BrokerDates.parse(row.getAny(BrokerHeaders.DATE))
        val signedQty = BrokerMoney.parseAmount(row.getAny(BrokerHeaders.QUANTITY))
        val reason = reject(date, signedQty, row, corporate)
        if (reason != null) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, reason, date)
        }
        return trade(sourceLine, date!!, signedQty!!, row)
    }

    private fun reject(date: LocalDate?, qty: BigDecimal?, row: CsvRow, corporate: Set<String>): String? = when {
        date == null -> "Unreadable date"
        qty == null -> "Missing quantity"
        row.getAny(BrokerHeaders.PRODUCT).isBlank() && row.get("ISIN").isBlank() -> "Missing product"
        corporateHit(date, row, corporate) -> "Corporate action"
        zeroPrice(row) -> "Zero price"
        else -> null
    }

    private fun corporateHit(date: LocalDate, row: CsvRow, corporate: Set<String>): Boolean {
        if (!zeroPrice(row)) return false
        val isin = row.get("ISIN")
        return isin.isNotBlank() && "$date|$isin" in corporate
    }

    private fun trade(sourceLine: Int, date: LocalDate, signedQty: BigDecimal, row: CsvRow): BrokerCsvLine {
        val product = row.getAny(BrokerHeaders.PRODUCT)
        val isin = row.get("ISIN").ifBlank { null }
        val booked = BrokerMoney.book(money(row, signedQty.abs()))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Missing price", date)
        val symbol = BrokerQuoteSymbol.fromDegiro(product, isin)
        val type = if (signedQty.signum() < 0) TransactionType.SELL else TransactionType.BUY
        return BrokerLines.degiroHolding(
            BrokerCsvFormat.DEGIRO_TRANSACTIONS,
            sourceLine,
            date,
            type,
            symbol,
            product,
            isin,
            booked,
        )
    }

    private fun money(row: CsvRow, quantity: BigDecimal): MoneyParts {
        val (priceCcy, price) = row.pairedAny(BrokerHeaders.PRICE)
        val eurValue = firstFilled(row, "Valor EUR", "Value EUR", "Waarde EUR", "Wert EUR")
        val (valueCcy, total) = if (eurValue.isNotBlank()) {
            "EUR" to eurValue
        } else {
            row.pairedAny(BrokerHeaders.VALUE + BrokerHeaders.TOTAL)
        }
        val namedLocal = row.getAny(BrokerHeaders.LOCAL_CCY)
        val namedValue = row.getAny(BrokerHeaders.VALUE_CCY)
        return MoneyParts(
            quantity = quantity.toPlainString(),
            price = price,
            priceCurrency = namedLocal.ifBlank { priceCcy },
            total = total,
            totalCurrency = if (eurValue.isNotBlank()) "EUR" else namedValue.ifBlank { valueCcy }.ifBlank { "EUR" },
            exchangeRate = row.getAny(BrokerHeaders.FX),
            fees = feeText(row),
            feesCurrency = "EUR",
        )
    }

    private fun feeText(row: CsvRow): String {
        val costs = BrokerMoney.absAmount(row.getAny(BrokerHeaders.FEES)) ?: BigDecimal.ZERO
        val auto = BrokerMoney.absAmount(firstFilled(row, "Taxa Autofx", "AutoFX", "Autofx")) ?: BigDecimal.ZERO
        return costs.add(auto).toPlainString()
    }

    private fun firstFilled(row: CsvRow, vararg names: String): String {
        for (name in names) {
            val value = row.get(name)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun corporateKeys(table: CsvTable): Set<String> {
        val grouped = linkedMapOf<String, MutableList<BigDecimal>>()
        for (cells in table.rows) {
            addZero(grouped, CsvRow(table.headers, cells))
        }
        return grouped.filterValues(::equalOpposite).keys
    }

    private fun addZero(grouped: MutableMap<String, MutableList<BigDecimal>>, row: CsvRow) {
        val qty = zeroQty(row) ?: return
        val date = BrokerDates.parse(row.getAny(BrokerHeaders.DATE)) ?: return
        val isin = row.get("ISIN").ifBlank { return }
        grouped.getOrPut("$date|$isin") { mutableListOf() }.add(qty)
    }

    private fun zeroQty(row: CsvRow): BigDecimal? {
        if (!zeroPrice(row)) return null
        return BrokerMoney.parseAmount(row.getAny(BrokerHeaders.QUANTITY))
    }

    private fun zeroPrice(row: CsvRow): Boolean {
        val price = BrokerMoney.parseAmount(row.pairedAny(BrokerHeaders.PRICE).second)
        return price != null && price.signum() == 0
    }

    private fun equalOpposite(qtys: List<BigDecimal>): Boolean =
        qtys.any { left -> left.signum() != 0 && qtys.any { right -> left.negate().compareTo(right) == 0 } }
}
