package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal

internal object DegiroAccountParser {
    private val buySellQty = Regex("(?i)(?:koop|buy|verkoop|sell)\\s+([0-9]+(?:[.,][0-9]+)?)")

    fun parse(table: CsvTable): List<BrokerCsvLine> {
        val lines = ArrayList<BrokerCsvLine>()
        table.forEachRow { sourceLine, row ->
            lines += parseRow(sourceLine, row)
        }
        return lines
    }

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date = BrokerDates.parse(row.get("Datum", "Date", "Value date", "Valutadatum"))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Unreadable date")
        val description = row.get("Omschrijving", "Description")
        val kind = classify(description)
        val change = BrokerMoney.parseAmount(row.get("Mutatie", "Change")) ?: BigDecimal.ZERO
        return when (kind) {
            AccountKind.SKIP -> BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Ignored $description", date)
            AccountKind.DEPOSIT -> cash(sourceLine, date, TransactionType.DEPOSIT_CASH, change.abs(), row)
            AccountKind.WITHDRAWAL -> cash(sourceLine, date, TransactionType.WITHDRAWAL, change.abs(), row)
            AccountKind.DIVIDEND -> dividend(sourceLine, date, row, change.abs())
            AccountKind.BUY, AccountKind.SELL -> trade(sourceLine, date, kind, row, change)
        }
    }

    private fun cash(sourceLine: Int, date: java.time.LocalDate, type: TransactionType, amount: BigDecimal, row: CsvRow): BrokerCsvLine {
        if (amount.signum() <= 0) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Cash amount missing", date)
        }
        return BrokerLines.cash(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, date, type, amount, row.get("Order Id", "Order ID"))
    }

    private fun dividend(sourceLine: Int, date: java.time.LocalDate, row: CsvRow, amount: BigDecimal): BrokerCsvLine {
        val product = row.get("Product")
        val isin = row.get("ISIN").ifBlank { null }
        if ((product.isBlank() && isin == null) || amount.signum() <= 0) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Dividend missing product or amount", date)
        }
        val symbol = BrokerQuoteSymbol.fromDegiro(product, isin)
        val booked = BookedAmounts(BigDecimal.ONE, amount, com.pirlruc.finsilo.domain.model.Currency.EUR, BigDecimal.ZERO, null)
        return BrokerLines.holding(
            HoldingDraft(
                format = BrokerCsvFormat.DEGIRO_ACCOUNT,
                sourceLine = sourceLine,
                date = date,
                type = TransactionType.DIVIDEND,
                symbol = symbol,
                name = product,
                isin = isin,
                quoteSymbol = null,
                booked = booked,
                externalId = row.get("Order Id", "Order ID"),
            ),
        )
    }

    private fun trade(sourceLine: Int, date: java.time.LocalDate, kind: AccountKind, row: CsvRow, change: BigDecimal): BrokerCsvLine {
        val match = buySellQty.find(row.get("Omschrijving", "Description"))
        if (match == null) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Account buy/sell needs Transactions.csv", date)
        }
        val qty = match.groupValues[1]
        val product = row.get("Product")
        val isin = row.get("ISIN").ifBlank { null }
        val booked =
            BrokerMoney.book(
                MoneyParts(qty, "", "EUR", change.abs().toPlainString(), "EUR", row.get("FX"), "", "EUR"),
            ) ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Account trade missing amount", date)
        val symbol = BrokerQuoteSymbol.fromDegiro(product, isin)
        return BrokerLines.holding(
            HoldingDraft(
                format = BrokerCsvFormat.DEGIRO_ACCOUNT,
                sourceLine = sourceLine,
                date = date,
                type = if (kind == AccountKind.BUY) TransactionType.BUY else TransactionType.SELL,
                symbol = symbol,
                name = product,
                isin = isin,
                quoteSymbol = null,
                booked = booked,
                externalId = row.get("Order Id", "Order ID"),
            ),
        )
    }

    private fun classify(description: String): AccountKind {
        val text = description.lowercase()
        return dividendKind(text) ?: cashKind(text) ?: tradeKind(text) ?: AccountKind.SKIP
    }

    private fun dividendKind(text: String): AccountKind? = when {
        text.contains("dividendbelasting") || text.contains("dividend tax") -> AccountKind.SKIP
        text.contains("dividend") -> AccountKind.DIVIDEND
        else -> null
    }

    private fun cashKind(text: String): AccountKind? = when {
        text.contains("storting") || text.contains("deposit") -> AccountKind.DEPOSIT
        text.contains("opname") || text.contains("withdrawal") -> AccountKind.WITHDRAWAL
        else -> null
    }

    private fun tradeKind(text: String): AccountKind? = when {
        text.startsWith("koop") || text.startsWith("buy") -> AccountKind.BUY
        text.startsWith("verkoop") || text.startsWith("sell") -> AccountKind.SELL
        else -> null
    }

    private enum class AccountKind { BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL, SKIP }
}
