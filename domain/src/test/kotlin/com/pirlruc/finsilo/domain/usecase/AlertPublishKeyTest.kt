package com.pirlruc.finsilo.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AlertPublishKeyTest {
    @Test
    fun digestIsStableAndDoesNotContainTheAlertText() {
        val first = AlertPublishKey.digest("RATING", "AAPL Buy", "AAPL moved to Buy")
        val again = AlertPublishKey.digest("RATING", "AAPL Buy", "AAPL moved to Buy")
        val other = AlertPublishKey.digest("RATING", "AAPL Buy", "AAPL moved to Hold")
        assertEquals(first, again)
        assertNotEquals(first, other)
        assertEquals(64, first.length)
        assertFalse(first.contains("AAPL"))
        assertFalse(first.contains("Buy"))
    }
}
