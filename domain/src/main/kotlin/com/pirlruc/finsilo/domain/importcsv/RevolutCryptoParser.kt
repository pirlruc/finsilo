package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType

/** Revolut crypto statement: Symbol, Type, Quantity, Price, Value, Fees, Date. */
internal object RevolutCryptoParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine = StatementRows.dispatch(
        format = BrokerCsvFormat.REVOLUT_STOCKS,
        sourceLine = sourceLine,
        dateRaw = row.get("Date"),
        actionLabel = row.get("Type"),
        type = actionType(normalizeHeader(row.get("Type"))),
        cash = { date, _ -> BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Ignored cash", date) },
        trade = { date, type -> tradeRow(sourceLine, date, type, row) },
    )

    private fun tradeRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val symbol = row.get("Symbol", "Ticker")
        val reward = reward(normalizeHeader(row.get("Type")))
        return StatementRows.bookedHolding(
            ListedTrade(
                format = BrokerCsvFormat.REVOLUT_STOCKS,
                sourceLine = sourceLine,
                date = date,
                type = type,
                ticker = symbol,
                isin = null,
                name = symbol,
                quote = symbol,
                booked = BrokerMoney.book(money(row, reward)),
                missingInstrument = "Trade missing ticker",
                missingBook = StatementRows.skipBook(money(row, reward), "Trade missing quantity/price"),
            ),
        )
    }

    private fun money(row: CsvRow, reward: Boolean): MoneyParts {
        val price = if (reward) "0" else row.get("Price")
        val total = if (reward) "0" else row.get("Value")
        val currency = if (reward) "EUR" else euroOrPrefix(price, row.get("Value"))
        return MoneyParts(
            quantity = row.get("Quantity"),
            price = price,
            priceCurrency = currency,
            total = total,
            totalCurrency = currency,
            exchangeRate = "",
            fees = if (reward) "0" else row.get("Fees"),
            feesCurrency = currency,
        )
    }

    private fun euroOrPrefix(price: String, value: String): String {
        val blob = price + value
        val euro = blob.contains("\u20ac") || blob.contains("\u00e2\u0082\u00ac") || blob.contains("EUR", ignoreCase = true)
        if (euro) return "EUR"
        return BrokerMoney.currencyPrefix(price).ifBlank { "EUR" }
    }

    private fun actionType(type: String): TransactionType? = when {
        type == "stake" || type == "unstake" -> null
        reward(type) -> TransactionType.BUY
        type.contains("sell") || type.contains("venda") -> TransactionType.SELL
        type.contains("buy") || type.contains("compra") -> TransactionType.BUY
        else -> null
    }

    private fun reward(type: String): Boolean = "staking reward" in type || "learn reward" in type
}
