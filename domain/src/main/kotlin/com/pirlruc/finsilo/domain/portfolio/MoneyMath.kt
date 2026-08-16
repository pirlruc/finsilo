package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Currency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object MoneyMath {
    val CONTEXT: MathContext = MathContext(16, RoundingMode.HALF_EVEN)
    val SCALE: Int = 8
    val HUNDRED: BigDecimal = BigDecimal("100")
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(SCALE)

    fun bd(value: String): BigDecimal = BigDecimal(value)

    fun bd(value: Int): BigDecimal = BigDecimal.valueOf(value.toLong())

    fun plus(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, CONTEXT)

    fun minus(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, CONTEXT)

    fun times(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, CONTEXT)

    fun div(a: BigDecimal, b: BigDecimal): BigDecimal {
        require(b.signum() != 0) { "Division by zero" }
        return a.divide(b, SCALE, RoundingMode.HALF_EVEN)
    }

    fun max(a: BigDecimal, b: BigDecimal): BigDecimal = a.max(b)

    fun min(a: BigDecimal, b: BigDecimal): BigDecimal = a.min(b)

    fun percentOf(part: BigDecimal, total: BigDecimal): BigDecimal {
        if (total.signum() == 0) return ZERO
        return div(times(part, HUNDRED), total)
    }

    /**
     * Convert a native amount to EUR.
     *
     * [usdPerEur] is USD per 1 EUR (RFC example: 1.10). USD amounts become
     * `native / usdPerEur`, matching `unit_price_eur = unit_price_native / exchange_rate`.
     */
    fun toEur(amountNative: BigDecimal, currency: Currency, usdPerEur: BigDecimal): BigDecimal =
        when (currency) {
            Currency.EUR -> amountNative
            Currency.USD -> div(amountNative, usdPerEur)
        }
}
