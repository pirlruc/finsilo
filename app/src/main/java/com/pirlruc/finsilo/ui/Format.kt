package com.pirlruc.finsilo.ui

import com.pirlruc.finsilo.domain.model.Currency
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency as JavaCurrency
import java.util.Locale

private fun moneyFormat(code: String): NumberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    runCatching { this.currency = JavaCurrency.getInstance(code) }
    maximumFractionDigits = 2
    minimumFractionDigits = 2
}

private fun percentFormat(): NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 1
}

fun formatEur(amount: BigDecimal): String = moneyFormat("EUR").format(amount)

fun formatEur(amount: Double): String = moneyFormat("EUR").format(amount)

fun formatNative(amount: BigDecimal, currency: Currency): String = when (currency) {
    Currency.GBX -> "${percentFormat().format(amount)}p"
    else -> moneyFormat(currency.name).format(amount)
}

fun formatPercent(value: BigDecimal): String = "${percentFormat().format(value)}%"

fun formatSignedEur(amount: BigDecimal): String {
    val formatted = formatEur(amount.abs())
    return when (amount.signum()) {
        1 -> "+$formatted"
        -1 -> "-$formatted"
        else -> formatted
    }
}
