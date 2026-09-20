package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType

internal object RevolutStocksParser {
    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date = BrokerDates.parse(row.get("Date", "Fecha", "Data", "Date started", "Date completed"))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Unreadable date")
        val kind = row.get("Type", "Tipo").lowercase()
        val type = actionType(kind)
        if (type == null) return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Ignored ${row.get("Type", "Tipo")}", date)
        if (type == TransactionType.DEPOSIT_CASH || type == TransactionType.WITHDRAWAL || type == TransactionType.INTEREST) {
            return cashRow(sourceLine, date, type, row)
        }
        return tradeRow(sourceLine, date, type, row)
    }

    private fun cashRow(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, row: CsvRow): BrokerCsvLine {
        val amount = BrokerMoney.absAmount(row.get("Total Amount", "Total", "Importe total", "Montante total"))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Cash row missing amount", date)
        val eur = BrokerMoney.toEurCash(amount, row.get("Currency", "Divisa", "Moeda"), row.get("FX Rate", "FX", "Tipo de cambio"))
            ?: return BrokerLines.skip(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, "Cash currency cannot be booked in EUR", date)
        return BrokerLines.cash(BrokerCsvFormat.REVOLUT_STOCKS, sourceLine, date, type, eur)
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
