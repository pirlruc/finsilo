package com.pirlruc.finsilo.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DecimalParseTest {

    @Test
    fun europeanCommaIsDecimalSeparator() {
        assertMoney("12.50", parseDecimal("12,50"))
        assertMoney("1234.56", parseDecimal("1.234,56"))
    }

    @Test
    fun usCommaIsGroupingWhenDotIsDecimal() {
        assertMoney("1234.56", parseDecimal("1,234.56"))
        assertMoney("12.50", parseDecimal("12.50"))
    }

    @Test
    fun blankAndInvalidReturnNull() {
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("   "))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("1.234.56"))
    }

    @Test
    fun trimsWhitespace() {
        assertMoney("10", parseDecimal("  10  "))
    }

    private fun assertMoney(expected: String, actual: BigDecimal?) {
        requireNotNull(actual) { "expected $expected but was null" }
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected was ${actual.toPlainString()}")
    }
}
