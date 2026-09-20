package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal

internal object DegiroTransactionsParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date = BrokerDates.parse(row.getAny(BrokerHeaders.DATE))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Unreadable date")
        val signedQty = BrokerMoney.parseAmount(row.getAny(BrokerHeaders.QUANTITY))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Missing quantity", date)
        val product = row.getAny(BrokerHeaders.PRODUCT)
        val isin = row.get("ISIN").ifBlank { null }
        if (product.isBlank() && isin == null) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Missing product", date)
        }
        return trade(sourceLine, date, signedQty, product, isin, row)
    }

    private fun trade(
        sourceLine: Int,
        date: java.time.LocalDate,
        signedQty: BigDecimal,
        product: String,
        isin: String?,
        row: CsvRow,
    ): BrokerCsvLine {
        val booked = BrokerMoney.book(money(row, signedQty.abs()))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Missing price", date)
        val symbol = BrokerQuoteSymbol.fromDegiro(product, isin)
        val type = if (signedQty.signum() < 0) TransactionType.SELL else TransactionType.BUY
        return BrokerLines.holding(
            HoldingDraft(
                format = BrokerCsvFormat.DEGIRO_TRANSACTIONS,
                sourceLine = sourceLine,
                date = date,
                type = type,
                symbol = symbol,
                name = product,
                isin = isin,
                quoteSymbol = null,
                booked = booked,
            ),
        )
    }

    private fun money(row: CsvRow, quantity: BigDecimal): MoneyParts {
        val (priceCcy, price) = row.pairedAny(BrokerHeaders.PRICE)
        val (valueCcy, total) = row.pairedAny(BrokerHeaders.VALUE + BrokerHeaders.TOTAL)
        val namedLocal = row.getAny(BrokerHeaders.LOCAL_CCY)
        val namedValue = row.getAny(BrokerHeaders.VALUE_CCY)
        return MoneyParts(
            quantity = quantity.toPlainString(),
            price = price,
            priceCurrency = namedLocal.ifBlank { priceCcy },
            total = total,
            totalCurrency = namedValue.ifBlank { valueCcy }.ifBlank { "EUR" },
            exchangeRate = row.getAny(BrokerHeaders.FX),
            fees = row.getAny(BrokerHeaders.FEES),
            feesCurrency = "EUR",
        )
    }
}
