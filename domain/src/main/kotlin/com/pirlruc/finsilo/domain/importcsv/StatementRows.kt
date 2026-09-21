package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

internal data class CashBooking(
    val format: BrokerCsvFormat,
    val sourceLine: Int,
    val date: LocalDate,
    val type: TransactionType,
    val amount: BigDecimal?,
    val currency: String,
    val exchangeRate: String,
    val missing: String,
    val skipNonPositive: Boolean,
)

internal data class ListedTrade(
    val format: BrokerCsvFormat,
    val sourceLine: Int,
    val date: LocalDate,
    val type: TransactionType,
    val ticker: String,
    val isin: String?,
    val name: String,
    val quote: String,
    val booked: BookedAmounts?,
    val missingInstrument: String,
    val missingBook: String,
)

/** Shared date/action dispatch for broker action-statement CSVs. */
internal object StatementRows {
    fun dispatch(
        format: BrokerCsvFormat,
        sourceLine: Int,
        dateRaw: String,
        actionLabel: String,
        type: TransactionType?,
        cash: (LocalDate, TransactionType) -> BrokerCsvLine,
        trade: (LocalDate, TransactionType) -> BrokerCsvLine,
    ): BrokerCsvLine {
        val date = BrokerDates.parse(dateRaw) ?: return BrokerLines.skip(format, sourceLine, "Unreadable date")
        if (type == null) return BrokerLines.skip(format, sourceLine, "Ignored $actionLabel", date)
        val movement = type == TransactionType.DEPOSIT_CASH ||
            type == TransactionType.WITHDRAWAL ||
            type == TransactionType.INTEREST
        return if (movement) cash(date, type) else trade(date, type)
    }

    fun cash(booking: CashBooking): BrokerCsvLine {
        val amount = booking.amount
        if (amount == null || (booking.skipNonPositive && amount.signum() <= 0)) {
            return BrokerLines.skip(booking.format, booking.sourceLine, booking.missing, booking.date)
        }
        val eur = BrokerMoney.toEurCash(amount, booking.currency, booking.exchangeRate)
            ?: return BrokerLines.skip(booking.format, booking.sourceLine, "Cash currency cannot be booked in EUR", booking.date)
        return BrokerLines.cash(booking.format, booking.sourceLine, booking.date, booking.type, eur)
    }

    fun bookedHolding(trade: ListedTrade): BrokerCsvLine {
        if (trade.ticker.isBlank() && trade.isin == null) {
            return BrokerLines.skip(trade.format, trade.sourceLine, trade.missingInstrument, trade.date)
        }
        val amounts = trade.booked
            ?: return BrokerLines.skip(trade.format, trade.sourceLine, trade.missingBook, trade.date)
        return BrokerLines.holding(
            HoldingDraft(
                format = trade.format,
                sourceLine = trade.sourceLine,
                date = trade.date,
                type = trade.type,
                symbol = trade.quote,
                name = trade.name,
                isin = trade.isin,
                quoteSymbol = trade.quote,
                booked = amounts,
            ),
        )
    }
}
