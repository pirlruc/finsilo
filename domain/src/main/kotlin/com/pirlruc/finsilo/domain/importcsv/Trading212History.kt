package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.BrokerReference
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Maps Trading 212 read-only history payloads into the same rows as [Trading212CsvParser].
 * Only filled history is booked. The caller must not place orders.
 */
object Trading212History {
    private val json = Json { ignoreUnknownKeys = true }

    fun lines(orderPages: List<String>, dividendPages: List<String>, transactionPages: List<String>): List<BrokerCsvLine> {
        val orders = orderPages.flatMapIndexed { page, body ->
            items(body).mapIndexed { index, item -> orderLine(page * PAGE + index, item) }
        }
        val dividends = dividendPages.flatMapIndexed { page, body ->
            items(body).mapIndexed { index, item ->
                dividendLine(
                    page * PAGE + index,
                    item,
                )
            }
        }
        val cash = transactionPages.flatMapIndexed { page, body ->
            items(body).mapIndexed { index, item -> cashLine(page * PAGE + index, item) }
        }
        return orders + dividends + cash
    }

    fun nextPath(page: String): String? = text(root(page), "nextPagePath")

    /** Keeps history cursors on the read-only history paths. Order placement paths are refused. */
    fun cursorPath(raw: String): String? {
        val path = hostPath(raw.trim()) ?: return null
        val bare = path.substringBefore('?')
        if (bare.contains("/orders") && !bare.contains("/history/")) return null
        if (!bare.startsWith("/api/v0/equity/history/")) return null
        return path
    }

    private fun hostPath(raw: String): String? {
        if (raw.isEmpty()) return null
        if (raw.startsWith("https://live.trading212.com")) return raw.removePrefix("https://live.trading212.com")
        if (raw.startsWith("https://")) return null
        return raw
    }

    private fun orderLine(index: Int, item: JsonObject): BrokerCsvLine {
        val order = obj(item, "order")
        val fill = obj(item, "fill")
        val status = text(order, "status") ?: "FILLED"
        if (!status.equals("FILLED", ignoreCase = true) || fill == null) {
            return BrokerLines.skip(BrokerCsvFormat.TRADING_212, index, "Ignored $status")
        }
        return filledOrder(index, order, fill)
    }

    private fun filledOrder(index: Int, order: JsonObject?, fill: JsonObject): BrokerCsvLine {
        val ticker = text(order, "ticker").orEmpty()
        val date = instant(text(fill, "filledAt"))
        val side = (text(fill, "type") ?: text(order, "side")).orEmpty()
        val type = if (side.equals("SELL", ignoreCase = true)) TransactionType.SELL else TransactionType.BUY
        if (date == null) return BrokerLines.skip(BrokerCsvFormat.TRADING_212, index, "Unreadable date")
        val wallet = obj(fill, "walletImpact")
        val parts = orderParts(fill, wallet)
        val quote = BrokerQuoteSymbol.fromTrading212(ticker, null)
        val reference = BrokerReference.canonical(text(fill, "id") ?: text(order, "id"))
        return StatementRows.bookedHolding(
            ListedTrade(
                format = BrokerCsvFormat.TRADING_212,
                sourceLine = index,
                date = date,
                type = type,
                ticker = ticker,
                isin = null,
                name = ticker,
                quote = quote,
                booked = BrokerMoney.book(parts),
                missingInstrument = "Trade missing ticker",
                missingBook = StatementRows.skipBook(parts, "Trade missing quantity/price"),
            ),
        ).copy(brokerReference = reference)
    }

    private fun orderParts(fill: JsonObject, wallet: JsonObject?): MoneyParts {
        val currency = text(wallet, "currency").orEmpty()
        return MoneyParts(
            quantity = text(fill, "quantity").orEmpty(),
            price = text(fill, "price").orEmpty(),
            priceCurrency = currency,
            total = text(wallet, "netValue").orEmpty(),
            totalCurrency = currency,
            exchangeRate = text(wallet, "fxRate").orEmpty(),
            fees = feeSum(wallet),
            feesCurrency = currency,
        )
    }

    private fun dividendLine(index: Int, item: JsonObject): BrokerCsvLine {
        val ticker = text(item, "ticker").orEmpty()
        val date = instant(text(item, "paidOn"))
        if (date == null) return BrokerLines.skip(BrokerCsvFormat.TRADING_212, index, "Unreadable date")
        val parts = MoneyParts(
            quantity = text(item, "quantity").orEmpty().ifBlank { "1" },
            price = text(item, "grossAmountPerShare") ?: text(item, "amount").orEmpty(),
            priceCurrency = text(item, "currency").orEmpty().ifBlank { "EUR" },
            total = text(item, "amount").orEmpty(),
            totalCurrency = text(item, "currency").orEmpty().ifBlank { "EUR" },
            exchangeRate = "",
            fees = "0",
            feesCurrency = "EUR",
        )
        val quote = BrokerQuoteSymbol.fromTrading212(ticker, null)
        return StatementRows.bookedHolding(
            ListedTrade(
                format = BrokerCsvFormat.TRADING_212,
                sourceLine = index,
                date = date,
                type = TransactionType.DIVIDEND,
                ticker = ticker,
                isin = null,
                name = ticker,
                quote = quote,
                booked = BrokerMoney.book(parts),
                missingInstrument = "Trade missing ticker",
                missingBook = StatementRows.skipBook(parts, "Trade missing quantity/price"),
            ),
        ).copy(brokerReference = BrokerReference.canonical(text(item, "reference")))
    }

    private fun cashLine(index: Int, item: JsonObject): BrokerCsvLine {
        val date = instant(text(item, "dateTime"))
        val type = cashType(text(item, "type").orEmpty())
        if (date == null) return BrokerLines.skip(BrokerCsvFormat.TRADING_212, index, "Unreadable date")
        if (type == null) return BrokerLines.skip(BrokerCsvFormat.TRADING_212, index, "Ignored ${text(item, "type")}", date)
        val currency = text(item, "currency").orEmpty().ifBlank { "EUR" }
        val line = StatementRows.cash(
            CashBooking(
                format = BrokerCsvFormat.TRADING_212,
                sourceLine = index,
                date = date,
                type = type,
                amount = text(item, "amount")?.let { BrokerMoney.absAmount(it) },
                currency = currency,
                exchangeRate = text(item, "fxRate").orEmpty(),
                missing = "Cash row missing Total",
                skipNonPositive = true,
            ),
        )
        return line.copy(brokerReference = BrokerReference.canonical(text(item, "reference")))
    }

    private fun cashType(raw: String): TransactionType? = when {
        raw.equals("DEPOSIT", ignoreCase = true) -> TransactionType.DEPOSIT_CASH
        raw.equals("WITHDRAWAL", ignoreCase = true) -> TransactionType.WITHDRAWAL
        raw.equals("INTEREST", ignoreCase = true) -> TransactionType.INTEREST
        else -> null
    }

    private fun feeSum(wallet: JsonObject?): String {
        val fees = wallet?.get("fees") as? JsonArray ?: return "0"
        val total = fees.mapNotNull { row -> text(row as? JsonObject, "quantity") }.mapNotNull { BrokerMoney.absAmount(it) }
            .fold(BigDecimal.ZERO) { acc, fee -> acc.add(fee) }
        return total.toPlainString()
    }

    private fun items(page: String): List<JsonObject> {
        val array = root(page)["items"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }

    private fun root(page: String): JsonObject = json.parseToJsonElement(page) as? JsonObject ?: JsonObject(emptyMap())

    private fun obj(parent: JsonObject?, key: String): JsonObject? = parent?.get(key) as? JsonObject

    private fun text(parent: JsonObject?, key: String): String? {
        val element = parent?.get(key) ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun instant(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    private const val PAGE = 1000
}
