package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType

internal object RevolutStocksParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> {
        val lines = ArrayList<BrokerCsvLine>()
        table.forEachRow { sourceLine, row ->
            lines += parseRow(sourceLine, row)
        }
        return lines
    }

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date = BrokerDates.parse(row.get("Date"))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Unreadable date")
        val kind = row.get("Type").lowercase()
        val type = actionType(kind)
        if (type == null) return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Ignored ${row.get("Type")}", date)
        if (type == TransactionType.DEPOSIT_CASH || type == TransactionType.WITHDRAWAL) {
            return cashRow(sourceLine, date, type, row)
        }
        return tradeRow(sourceLine, date, type, row)
    }

    private fun cashRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val amount = BrokerMoney.absAmount(row.get("Total Amount", "Total"))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Cash row missing amount", date)
        val eur = BrokerMoney.toEurCash(amount, row.get("Currency"), row.get("FX Rate", "FX"))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Cash currency cannot be booked in EUR", date)
        return BrokerLines.cash(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, date, type, eur, row.get("ID"))
    }

    private fun tradeRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val ticker = row.get("Ticker", "Symbol")
        val isin = row.get("ISIN").ifBlank { null }
        if (ticker.isBlank() && isin == null) {
            return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Trade missing ticker", date)
        }
        val booked = BrokerMoney.book(money(row, type))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Trade missing quantity/price", date)
        val quote = BrokerQuoteSymbol.fromIsin(ticker.ifBlank { isin.orEmpty() }, isin)
        val fx = booked.eurPerUsd ?: BrokerMoney.eurPerUsdRate(row.get("FX Rate", "FX"))
        return BrokerLines.holding(
            HoldingDraft(
                format = BrokerCsvFormat.REVOLUT_STOCKS,
                sourceLine = sourceLine,
                date = date,
                type = type,
                symbol = quote,
                name = ticker.ifBlank { isin.orEmpty() },
                isin = isin,
                quoteSymbol = quote,
                booked = booked.copy(eurPerUsd = fx),
                externalId = row.get("ID"),
            ),
        )
    }

    private fun money(row: CsvRow, type: TransactionType): MoneyParts {
        val qty = row.get("Quantity").ifBlank { if (type == TransactionType.DIVIDEND) "1" else "" }
        val price = row.get("Price per share").ifBlank { row.get("Total Amount", "Total") }
        val currency = row.get("Currency")
        return MoneyParts(
            quantity = qty,
            price = price,
            priceCurrency = currency.ifBlank { BrokerMoney.currencyPrefix(price) },
            total = row.get("Total Amount", "Total"),
            totalCurrency = currency,
            exchangeRate = row.get("FX Rate", "FX"),
            fees = row.get("Fees"),
            feesCurrency = currency,
        )
    }

    private fun actionType(type: String): TransactionType? = when {
        type.contains("top-up") || type.contains("top up") || type.contains("deposit") -> TransactionType.DEPOSIT_CASH
        type.contains("withdraw") -> TransactionType.WITHDRAWAL
        type.contains("dividend") -> TransactionType.DIVIDEND
        type.contains("buy") -> TransactionType.BUY
        type.contains("sell") -> TransactionType.SELL
        else -> null
    }
}
