package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal

internal object DegiroAccountParser {
    private val buySellQty =
        Regex("(?i)(?:koop|buy|verkoop|sell|compra|venda|achat|vente|kauf|verkauf|acquisto|vendita)\\s+([0-9]+(?:[.,][0-9]+)?)")

    fun parse(table: CsvTable): List<BrokerCsvLine> = table.mapRows(::parseRow)

    private fun parseRow(sourceLine: Int, row: CsvRow): BrokerCsvLine {
        val date =
            BrokerDates.parse(row.get("Datum", "Date", "Data", "Fecha", "Value date", "Valutadatum", "Data valor"))
                ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Unreadable date")
        val description = row.getAny(BrokerHeaders.DESCRIPTION)
        val kind = classify(description)
        val (currency, rawAmount) = row.pairedAny(BrokerHeaders.CHANGE)
        val change = BrokerMoney.parseAmount(rawAmount) ?: BigDecimal.ZERO
        return when (kind) {
            AccountKind.SKIP -> BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Ignored $description", date)
            AccountKind.DEPOSIT -> cash(sourceLine, date, TransactionType.DEPOSIT_CASH, change.abs(), currency, row)
            AccountKind.WITHDRAWAL -> cash(sourceLine, date, TransactionType.WITHDRAWAL, change.abs(), currency, row)
            AccountKind.INTEREST -> cash(sourceLine, date, TransactionType.INTEREST, change.abs(), currency, row)
            AccountKind.DIVIDEND -> dividend(sourceLine, date, row, change.abs())
            AccountKind.BUY, AccountKind.SELL -> trade(sourceLine, date, kind, row, change)
        }
    }

    private fun cash(
        sourceLine: Int,
        date: java.time.LocalDate,
        type: TransactionType,
        amount: BigDecimal,
        currency: String,
        row: CsvRow,
    ): BrokerCsvLine {
        if (amount.signum() <= 0) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Cash amount missing", date)
        }
        val eur = BrokerMoney.toEurCash(amount, currency, row.getAny(BrokerHeaders.FX))
            ?: return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Cash currency cannot be booked in EUR", date)
        return BrokerLines.cash(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, date, type, eur)
    }

    private fun dividend(sourceLine: Int, date: java.time.LocalDate, row: CsvRow, amount: BigDecimal): BrokerCsvLine {
        val product = row.getAny(BrokerHeaders.PRODUCT)
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
            ),
        )
    }

    private fun trade(sourceLine: Int, date: java.time.LocalDate, kind: AccountKind, row: CsvRow, change: BigDecimal): BrokerCsvLine {
        val match = buySellQty.find(row.getAny(BrokerHeaders.DESCRIPTION))
        if (match == null) {
            return BrokerLines.skip(BrokerCsvFormat.DEGIRO_ACCOUNT, sourceLine, "Account buy/sell needs Transactions.csv", date)
        }
        val qty = match.groupValues[1]
        val product = row.getAny(BrokerHeaders.PRODUCT)
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
            ),
        )
    }

    private fun classify(description: String): AccountKind {
        val text = normalizeHeader(description)
        return dividendKind(text) ?: interestKind(text) ?: cashKind(text) ?: tradeKind(text) ?: AccountKind.SKIP
    }

    private fun dividendKind(text: String): AccountKind? = when {
        text.contains("dividendbelasting") || text.contains("dividend tax") || text.contains("imposto sobre dividend") -> AccountKind.SKIP
        text.contains("dividend") || text.contains("dividendo") || text.contains("dividende") -> AccountKind.DIVIDEND
        else -> null
    }

    private fun interestKind(text: String): AccountKind? {
        if (interestWord(text)) return AccountKind.INTEREST
        return null
    }

    private fun interestWord(text: String): Boolean =
        listOf("interest", "rente", "juros", "intereses", "interets", "zinsen", "interessi", "flatex interest").any { it in text }

    private fun cashKind(text: String): AccountKind? = when {
        depositWord(text) -> AccountKind.DEPOSIT
        withdrawWord(text) -> AccountKind.WITHDRAWAL
        else -> null
    }

    private fun depositWord(text: String): Boolean =
        listOf("storting", "deposit", "deposito", "ingreso", "einzahlung", "depot").any { it in text }

    private fun withdrawWord(text: String): Boolean =
        listOf("opname", "withdrawal", "levantamento", "retirada", "retrait", "auszahlung", "prelievo").any { it in text }

    private fun tradeKind(text: String): AccountKind? = when {
        buyWord(text) -> AccountKind.BUY
        sellWord(text) -> AccountKind.SELL
        else -> null
    }

    private fun buyWord(text: String): Boolean = text.startsWith("koop") ||
        text.startsWith("buy") ||
        text.startsWith("compra") ||
        text.startsWith("achat") ||
        text.startsWith("kauf") ||
        text.startsWith("acquisto")

    private fun sellWord(text: String): Boolean = text.startsWith("verkoop") ||
        text.startsWith("sell") ||
        text.startsWith("venda") ||
        text.startsWith("vente") ||
        text.startsWith("verkauf") ||
        text.startsWith("vendita")

    private enum class AccountKind { BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL, INTEREST, SKIP }
}
