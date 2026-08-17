package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import java.math.BigDecimal

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
        val price = absAmount(parts.price)
        val total = absAmount(parts.total)
        val priceCcy = currencyCode(parts.priceCurrency.ifBlank { currencyPrefix(parts.price) }, parts.totalCurrency)
        val unit = unitPrice(quantity, price, total, priceCcy, parts) ?: return null
        return BookedAmounts(quantity, unit.first, unit.second, feesEur(parts, unit.third), unit.third)
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

    fun eurPerUsdRate(raw: String): BigDecimal? {
        val rate = absAmount(raw) ?: return null
        if (rate.signum() <= 0) return null
        val invert = rate > BigDecimal("1.05") && rate <= BigDecimal("1.70")
        return if (invert) div(BigDecimal.ONE, rate) else rate
    }

    private fun feesEur(parts: MoneyParts, eurPerUsd: BigDecimal?): BigDecimal {
        val fee = absAmount(parts.fees) ?: return BigDecimal.ZERO
        return when (currencyCode(parts.feesCurrency, parts.totalCurrency)) {
            "USD" -> eurPerUsd?.let { times(fee, it) } ?: BigDecimal.ZERO
            "EUR", "" -> fee
            else -> BigDecimal.ZERO
        }
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
