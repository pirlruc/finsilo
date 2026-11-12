package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertBoundRemainderTest {
    @Test
    fun boundDropsTheOldestDigestAndKeepsTheNextOne() {
        assertEquals(setOf("a"), AlertPublishKey.bound(emptySet(), "a", max = 2))
        assertEquals(setOf("b", "c"), AlertPublishKey.bound(setOf("a", "b"), "c", max = 2))
        assertEquals(64, AlertPublishKey.digest("c", "t", "b").length)
    }

    @Test
    fun priceAlertIsEmptyOnlyWhenBothLevelsAreMissing() {
        assertTrue(PriceAlertThreshold("a").isEmpty)
        assertFalse(PriceAlertThreshold("a", eurLevel = BigDecimal.ONE).isEmpty)
        assertFalse(PriceAlertThreshold("a", percentMove = BigDecimal.ONE).isEmpty)
        assertFalse(PriceAlertThreshold("a", BigDecimal.ONE, BigDecimal.ONE).isEmpty)
    }
}
