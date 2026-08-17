package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType

internal object Trading212CsvParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> {
        val lines = ArrayList<BrokerCsvLine>()
        table.forEachRow { sourceLine, row ->
            lines += parseRow(sourceLine, row)
        }
        return lines
    }

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val action = row.get("Action").lowercase()
        val date = BrokerDates.parse(row.get("Time"))
        val type = actionType(action)
        if (date == null) return BrokerLines.skip(BrokerCsvFormat.TRADING_212, sourceLine, "Unreadable date")
        if (type == null) return BrokerLines.skip(BrokerCsvFormat.TRADING_212, sourceLine, "Ignored ${row.get("Action")}", date)
        if (type == TransactionType.DEPOSIT_CASH || type == TransactionType.WITHDRAWAL) {
            return cashRow(sourceLine, date, type, row)
        }
        return tradeRow(sourceLine, date, type, row)
    }

    private fun cashRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val amount = BrokerMoney.absAmount(row.get("Total")) ?: BrokerMoney.absAmount(row.get("Result"))
        if (amount == null || amount.signum() <= 0) {
            return BrokerLines.skip(BrokerCsvFormat.TRADING_212, sourceLine, "Cash row missing Total", date)
        }
        return BrokerLines.cash(BrokerCsvFormat.TRADING_212, sourceLine, date, type, amount, row.get("ID"))
    }

    private fun tradeRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val ticker = row.get("Ticker")
        val isin = row.get("ISIN").ifBlank { null }
        if (ticker.isBlank() && isin == null) {
            return BrokerLines.skip(BrokerCsvFormat.TRADING_212, sourceLine, "Trade missing ticker", date)
        }
        val booked = BrokerMoney.book(money(row, type))
            ?: return BrokerLines.skip(BrokerCsvFormat.TRADING_212, sourceLine, "Trade missing quantity/price", date)
        val quote = BrokerQuoteSymbol.fromTrading212(ticker.ifBlank { isin.orEmpty() })
        return BrokerLines.holding(
            HoldingDraft(
                format = BrokerCsvFormat.TRADING_212,
                sourceLine = sourceLine,
                date = date,
                type = type,
                symbol = quote,
                name = row.get("Name"),
                isin = isin,
                quoteSymbol = quote,
                booked = booked,
                externalId = row.get("ID"),
            ),
        )
    }

    private fun money(row: CsvRow, type: TransactionType): MoneyParts {
        val shares = row.get("No. of shares").ifBlank { if (type == TransactionType.DIVIDEND) "1" else "" }
        val price = row.get("Price / share").ifBlank { row.get("Total") }
        return MoneyParts(
            quantity = shares,
            price = price,
            priceCurrency = row.get("Currency (Price / share)"),
            total = row.get("Total"),
            totalCurrency = row.get("Currency (Total)"),
            exchangeRate = row.get("Exchange rate"),
            fees = feeSum(row),
            feesCurrency = row.get("Currency (Charge amount)", "Currency (Total)"),
        )
    }

    private fun feeSum(row: CsvRow): String {
        val charge = BrokerMoney.absAmount(row.get("Charge amount")).orZero()
        val conversion = BrokerMoney.absAmount(row.get("Currency conversion fee", "Conversion fee")).orZero()
        val stamp = BrokerMoney.absAmount(row.get("Stamp duty reserve tax", "Stamp duty")).orZero()
        return charge.add(conversion).add(stamp).toPlainString()
    }

    private fun actionType(action: String): TransactionType? = when {
        action.contains("deposit") -> TransactionType.DEPOSIT_CASH
        action.contains("withdraw") -> TransactionType.WITHDRAWAL
        action.contains("dividend") -> TransactionType.DIVIDEND
        action.contains("interest") -> TransactionType.DEPOSIT_CASH
        action.contains("buy") -> TransactionType.BUY
        action.contains("sell") -> TransactionType.SELL
        else -> null
    }

    private fun java.math.BigDecimal?.orZero(): java.math.BigDecimal = this ?: java.math.BigDecimal.ZERO
}
