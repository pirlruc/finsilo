package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType

internal object RevolutStocksParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine = StatementRows.dispatch(
        format = BrokerCsvFormat.REVOLUT_STOCKS,
        sourceLine = sourceLine,
        dateRaw = row.get("Date", "Fecha", "Data", "Date started", "Date completed"),
        actionLabel = row.get("Type", "Tipo"),
        type = actionType(row.get("Type", "Tipo").lowercase()),
        cash = { date, type -> cashRow(sourceLine, date, type, row) },
        trade = { date, type -> tradeRow(sourceLine, date, type, row) },
    )

    private fun cashRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine = StatementRows.cash(
        CashBooking(
            format = BrokerCsvFormat.REVOLUT_STOCKS,
            sourceLine = sourceLine,
            date = date,
            type = type,
            amount = BrokerMoney.absAmount(row.get("Total Amount", "Total", "Importe total", "Montante total")),
            currency = row.get("Currency", "Divisa", "Moeda"),
            exchangeRate = row.get("FX Rate", "FX", "Tipo de cambio"),
            missing = "Cash row missing amount",
            skipNonPositive = false,
        ),
    )

    private fun tradeRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val ticker = row.get("Ticker", "Symbol")
        val isin = row.get("ISIN").ifBlank { null }
        val booked = BrokerMoney.book(money(row, type))
        val quote = BrokerQuoteSymbol.fromIsin(ticker.ifBlank { isin.orEmpty() }, isin)
        val fx = booked?.eurPerUsd ?: BrokerMoney.eurPerUsdRate(row.get("FX Rate", "FX"))
        return StatementRows.bookedHolding(
            ListedTrade(
                format = BrokerCsvFormat.REVOLUT_STOCKS,
                sourceLine = sourceLine,
                date = date,
                type = type,
                ticker = ticker,
                isin = isin,
                name = ticker.ifBlank { isin.orEmpty() },
                quote = quote,
                booked = booked?.copy(eurPerUsd = fx),
                missingInstrument = "Trade missing ticker",
                missingBook = "Trade missing quantity/price",
            ),
        )
    }

    private fun money(row: CsvRow, type: TransactionType): MoneyParts {
        val qty = row.get("Quantity", "Cantidad", "Quantidade").ifBlank { if (type == TransactionType.DIVIDEND) "1" else "" }
        val price = row.get("Price per share", "Precio por acción", "Preço por ação", "Price").ifBlank {
            row.get("Total Amount", "Total", "Importe total")
        }
        val currency = row.get("Currency", "Divisa", "Moeda")
        return MoneyParts(
            quantity = qty,
            price = price,
            priceCurrency = currency.ifBlank { BrokerMoney.currencyPrefix(price) },
            total = row.get("Total Amount", "Total", "Importe total", "Montante total"),
            totalCurrency = currency,
            exchangeRate = row.get("FX Rate", "FX", "Tipo de cambio"),
            fees = row.get("Fees"),
            feesCurrency = currency,
        )
    }

    private fun actionType(type: String): TransactionType? = when {
        cashIn(type) -> TransactionType.DEPOSIT_CASH
        type.contains("withdraw") -> TransactionType.WITHDRAWAL
        interestWord(type) -> TransactionType.INTEREST
        dividendWord(type) -> TransactionType.DIVIDEND
        buyWord(type) -> TransactionType.BUY
        sellWord(type) -> TransactionType.SELL
        else -> null
    }

    private fun cashIn(type: String): Boolean = type.contains("top-up") || type.contains("top up") || type.contains("deposit")

    private fun interestWord(type: String): Boolean = type.contains("interest") || type.contains("juros") || type.contains("intereses")

    private fun dividendWord(type: String): Boolean = type.contains("dividend") || type.contains("dividendo")

    private fun buyWord(type: String): Boolean = type.contains("buy") || type.contains("compra")

    private fun sellWord(type: String): Boolean = type.contains("sell") || type.contains("venta") || type.contains("venda")
}
