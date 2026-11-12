package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import java.math.BigDecimal
import java.math.BigDecimal as Jbd

/** Quantity, native unit price, booking currency, EUR fees, and optional EUR-per-USD. */
internal data class BookedAmounts(
    val quantity: BigDecimal,
    val unitPriceNative: BigDecimal,
    val currency: Currency,
    val feesEur: BigDecimal,
    val eurPerUsd: BigDecimal?,
)

/** Fields used to book a trade into EUR or USD. */
internal data class MoneyParts(
    val quantity: String,
    val price: String,
    val priceCurrency: String,
    val total: String,
    val totalCurrency: String,
    val exchangeRate: String,
    val fees: String,
    val feesCurrency: String,
)

/** Convert broker money columns into FinSilo EUR/USD booking. */
internal object BrokerMoney {
    fun parseAmount(raw: String): BigDecimal? = parseDecimal(numericPart(raw))

    fun absAmount(raw: String): BigDecimal? = parseAmount(raw)?.abs()

    fun book(parts: MoneyParts): BookedAmounts? {
        val quantity = absAmount(parts.quantity) ?: return null
        if (quantity.signum() <= 0) return null
        return booked(quantity, parts)
    }

    private fun booked(quantity: BigDecimal, parts: MoneyParts): BookedAmounts? {
        val price = absAmount(parts.price)
        val total = absAmount(parts.total)
        val priceCcy = currencyCode(parts.priceCurrency.ifBlank { currencyPrefix(parts.price) }, parts.totalCurrency)
        val unit = unitPrice(quantity, price, total, priceCcy, parts) ?: return null
        val fees = feesEur(parts, unit.third) ?: return null
        return BookedAmounts(quantity, unit.first, unit.second, fees, unit.third)
    }

    /** True when the trade itself books, but a non-zero fee has no EUR rate. */
    fun unconvertibleFee(parts: MoneyParts): Boolean {
        if (book(parts) != null) return false
        return book(parts.copy(fees = "0", feesCurrency = "EUR")) != null
    }

    private fun unitPrice(
        quantity: BigDecimal,
        price: BigDecimal?,
        total: BigDecimal?,
        priceCcy: String,
        parts: MoneyParts,
    ): Triple<BigDecimal, Currency, BigDecimal?>? = when (priceCcy) {
        "EUR", "" -> eurUnit(quantity, price, total)
        "USD" -> usdUnit(quantity, price, total, parts)
        "GBP", "CHF", "DKK", "SEK", "NOK" -> foreignUnit(quantity, price, total, priceCcy, parts)
        "GBX" -> if (currencyCode(parts.totalCurrency, "") == "EUR" && total != null) {
            eurTotalUnit(quantity, total, parts)
        } else {
            gbxUnit(quantity, price, total, parts)
        }
        else -> eurTotalUnit(quantity, total, parts)
    }

    private fun eurUnit(quantity: BigDecimal, price: BigDecimal?, total: BigDecimal?): Triple<BigDecimal, Currency, BigDecimal?>? {
        val unit = price ?: total?.let { div(it, quantity) } ?: return null
        return Triple(unit, Currency.EUR, null)
    }

    private fun usdUnit(
        quantity: BigDecimal,
        price: BigDecimal?,
        total: BigDecimal?,
        parts: MoneyParts,
    ): Triple<BigDecimal, Currency, BigDecimal?>? {
        if (price != null) return Triple(price, Currency.USD, usdFx(quantity, price, total, parts))
        return eurTotalUnit(quantity, total, parts)
    }

    private fun foreignUnit(
        quantity: BigDecimal,
        price: BigDecimal?,
        total: BigDecimal?,
        code: String,
        parts: MoneyParts,
    ): Triple<BigDecimal, Currency, BigDecimal?>? {
        val currency = Currency.fromCode(code) ?: return null
        val native = price ?: return eurTotalUnit(quantity, total, parts)
        val rate = fxFromEurTotal(quantity, native, total, currencyCode(parts.totalCurrency, ""))
            ?: plainFxRate(parts.exchangeRate)
            ?: return null
        return Triple(native, currency, rate)
    }

    private fun gbxUnit(
        quantity: BigDecimal,
        price: BigDecimal?,
        total: BigDecimal?,
        parts: MoneyParts,
    ): Triple<BigDecimal, Currency, BigDecimal?>? {
        val native = price ?: return null
        val rate = fxFromEurTotal(quantity, div(native, Jbd("100")), total, currencyCode(parts.totalCurrency, ""))
            ?: plainFxRate(parts.exchangeRate)
            ?: return null
        return Triple(native, Currency.GBX, rate)
    }

    private fun eurTotalUnit(quantity: BigDecimal, total: BigDecimal?, parts: MoneyParts): Triple<BigDecimal, Currency, BigDecimal?>? {
        val tot = total ?: return null
        if (currencyCode(parts.totalCurrency, "") != "EUR") return null
        return Triple(div(tot, quantity), Currency.EUR, null)
    }

    private fun usdFx(quantity: BigDecimal, native: BigDecimal, total: BigDecimal?, parts: MoneyParts): BigDecimal? {
        val totalCcy = currencyCode(parts.totalCurrency, "")
        val fromTotal = fxFromEurTotal(quantity, native, total, totalCcy)
        if (fromTotal != null) return fromTotal
        return eurPerUsdRate(parts.exchangeRate)
    }

    private fun fxFromEurTotal(quantity: BigDecimal, native: BigDecimal, total: BigDecimal?, totalCcy: String): BigDecimal? {
        if (total == null || totalCcy != "EUR") return null
        val notional = quantity.multiply(native)
        if (notional.signum() == 0) return null
        val rate = div(total, notional)
        return rate.takeIf { it.signum() > 0 }
    }

    /**
     * EUR per 1 USD. A quote in (1.05, 1.70] is treated as USD per EUR and inverted.
     * That band is a USD heuristic and must not be applied to other currencies.
     */
    fun eurPerUsdRate(raw: String): BigDecimal? {
        val rate = positiveRate(raw) ?: return null
        val invert = rate > BigDecimal("1.05") && rate <= BigDecimal("1.70")
        return if (invert) div(BigDecimal.ONE, rate) else rate
    }

    /** Positive FX quote with no USD invert band. An explicit EUR total wins over this. */
    fun plainFxRate(raw: String): BigDecimal? = positiveRate(raw)

    private fun positiveRate(raw: String): BigDecimal? {
        val rate = absAmount(raw) ?: return null
        if (rate.signum() <= 0) return null
        return rate
    }

    fun toEurCash(amount: BigDecimal, currency: String, exchangeRate: String): BigDecimal? {
        val code = currencyCode(currency, "")
        val converted = cashInEur(amount, code, exchangeRate)
        if (converted == null && code.isNotEmpty() && code != "EUR" && amount.signum() != 0) return null
        return converted
    }

    private fun cashInEur(amount: BigDecimal, code: String, exchangeRate: String): BigDecimal? = when (code) {
        "EUR", "" -> amount
        "USD" -> eurPerUsdRate(exchangeRate)?.let { times(amount, it) }
        "GBP", "CHF", "DKK", "SEK", "NOK" -> plainFxRate(exchangeRate)?.let { times(amount, it) }
        "GBX" -> plainFxRate(exchangeRate)?.let { times(div(amount, Jbd("100")), it) }
        else -> null
    }

    private fun feesEur(parts: MoneyParts, bookedRate: BigDecimal?): BigDecimal? {
        val fee = absAmount(parts.fees) ?: return BigDecimal.ZERO
        if (fee.signum() == 0) return BigDecimal.ZERO
        val code = currencyCode(parts.feesCurrency, parts.totalCurrency)
        val rate = feeRate(code, bookedRate, parts.exchangeRate) ?: return null
        return when (code) {
            "EUR", "" -> fee
            "GBX" -> times(div(fee, Jbd("100")), rate)
            else -> times(fee, rate)
        }
    }

    private fun feeRate(code: String, bookedRate: BigDecimal?, exchangeRate: String): BigDecimal? = when (code) {
        "EUR", "" -> BigDecimal.ONE
        "USD" -> bookedRate ?: eurPerUsdRate(exchangeRate)
        "GBP", "CHF", "DKK", "SEK", "NOK", "GBX" -> bookedRate ?: plainFxRate(exchangeRate)
        else -> null
    }

    internal fun currencyCode(primary: String, fallback: String): String {
        val raw = primary.ifBlank { fallback }.trim().uppercase()
        val token = raw.substringAfterLast('(').substringBefore(')').trim().ifBlank { raw }
        val letters = token.filter { it.isLetter() }
        return when {
            letters.contains("GBX") || letters == "GBP" && primary.contains("GBX") -> "GBX"
            letters.takeLast(3).length == 3 -> letters.takeLast(3)
            else -> letters
        }
    }

    internal fun currencyPrefix(raw: String): String {
        val letters = raw.trim().takeWhile { it.isLetter() }.uppercase()
        return if (letters.length == 3) letters else ""
    }

    internal fun numericPart(raw: String): String = raw.trim()
        .replace('\u00a0', ' ')
        .replace(" ", "")
        .filter { it.isDigit() || it == '.' || it == ',' || it == '-' || it == '+' }
}
