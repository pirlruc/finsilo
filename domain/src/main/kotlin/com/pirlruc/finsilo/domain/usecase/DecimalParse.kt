package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import java.math.BigDecimal

/**
 * Parse a decimal the way a Portuguese (or US) user types it.
 *
 * The last `,` or `.` is the decimal separator; the other mark is grouping.
 * A single comma ("12,50") is a decimal, matching European input.
 */
fun parseDecimal(raw: String): BigDecimal? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = normalizeDecimal(trimmed) ?: return null
    return runCatching { MoneyMath.bd(normalized) }.getOrNull()
}

internal fun normalizeDecimal(raw: String): String? {
    val lastComma = raw.lastIndexOf(',')
    val lastDot = raw.lastIndexOf('.')
    val decimalIsComma =
        lastComma >= 0 && (lastDot < 0 || lastComma > lastDot)
    val grouping = if (decimalIsComma) '.' else ','
    val withoutGrouping = raw.filter { it != grouping }
    val withDot = if (decimalIsComma) withoutGrouping.replace(',', '.') else withoutGrouping
    if (withDot.count { it == '.' } > 1) return null
    return withDot
}
