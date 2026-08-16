package com.pirlruc.finsilo.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val portugal = Locale("pt", "PT")

private val eurFormat: NumberFormat =
    NumberFormat.getCurrencyInstance(portugal).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

private val percentFormat: NumberFormat =
    NumberFormat.getNumberInstance(portugal).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
    }

fun formatEur(amount: BigDecimal): String = eurFormat.format(amount)

fun formatEur(amount: Double): String = eurFormat.format(amount)

fun formatPercent(value: BigDecimal): String = "${percentFormat.format(value)}%"

fun formatSignedEur(amount: BigDecimal): String {
    val formatted = formatEur(amount.abs())
    return when (amount.signum()) {
        1 -> "+$formatted"
        -1 -> "-$formatted"
        else -> formatted
    }
}
