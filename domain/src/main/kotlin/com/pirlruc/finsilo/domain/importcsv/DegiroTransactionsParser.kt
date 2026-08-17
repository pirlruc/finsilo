package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal

internal object DegiroTransactionsParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> {
        val lines = ArrayList<BrokerCsvLine>()
        table.forEachRow { sourceLine, row ->
            lines += parseRow(sourceLine, row)
        }
        return lines
    }

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date = BrokerDates.parse(row.get("Datum", "Date"))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Unreadable date")
        val signedQty = BrokerMoney.parseAmount(row.get("Aantal", "Quantity"))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_TRANSACTIONS, sourceLine, "Missing quantity", date)
        val product = row.get("Product")
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
                externalId = row.get("Order ID", "Order-ID", "Order Id"),
            ),
        )
    }

    private fun money(row: CsvRow, quantity: BigDecimal): MoneyParts {
        val localCcy = row.get("Local currency", "Lokale valuta")
        val valueCcy = row.get("Value currency", "Valuta")
        return MoneyParts(
            quantity = quantity.toPlainString(),
            price = row.get("Koers", "Price"),
            priceCurrency = localCcy,
            total = row.get("Waarde", "Value", "Totaal", "Total"),
            totalCurrency = valueCcy.ifBlank { "EUR" },
            exchangeRate = row.get("Wisselkoers", "Exchange rate"),
            fees = row.get("Transactiekosten", "Transaction and/or third-party fees", "Transaction costs"),
            feesCurrency = "EUR",
        )
    }
}
