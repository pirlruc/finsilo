package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType

internal object Trading212CsvParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine = StatementRows.dispatch(
        format = BrokerCsvFormat.TRADING_212,
        sourceLine = sourceLine,
        dateRaw = row.get("Time"),
        actionLabel = row.get("Action"),
        type = actionType(row.get("Action").lowercase()),
        cash = { date, type -> cashRow(sourceLine, date, type, row) },
        trade = { date, type -> tradeRow(sourceLine, date, type, row) },
    )

    private fun cashRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine = StatementRows.cash(
        CashBooking(
            format = BrokerCsvFormat.TRADING_212,
            sourceLine = sourceLine,
            date = date,
            type = type,
            amount = BrokerMoney.absAmount(row.get("Total")) ?: BrokerMoney.absAmount(row.get("Result")),
            currency = row.get("Currency (Total)"),
            exchangeRate = row.get("Exchange rate"),
            missing = "Cash row missing Total",
            skipNonPositive = true,
        ),
    )

    private fun tradeRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val ticker = row.get("Ticker")
        val isin = row.get("ISIN").ifBlank { null }
        val quote = BrokerQuoteSymbol.fromTrading212(ticker.ifBlank { isin.orEmpty() })
        return StatementRows.bookedHolding(
            ListedTrade(
                format = BrokerCsvFormat.TRADING_212,
                sourceLine = sourceLine,
                date = date,
                type = type,
                ticker = ticker,
                isin = isin,
                name = row.get("Name"),
                quote = quote,
                booked = BrokerMoney.book(money(row, type)),
                missingInstrument = "Trade missing ticker",
                missingBook = "Trade missing quantity/price",
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
        action.contains("interest") -> TransactionType.INTEREST
        action.contains("buy") -> TransactionType.BUY
        action.contains("sell") -> TransactionType.SELL
        else -> null
    }

    private fun java.math.BigDecimal?.orZero(): java.math.BigDecimal = this ?: java.math.BigDecimal.ZERO
}
