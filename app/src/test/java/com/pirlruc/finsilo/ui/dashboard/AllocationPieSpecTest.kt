package com.pirlruc.finsilo.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllocationPieSpecTest {
    @Test
    fun singleSliceIsAFullDisk() {
        assertEquals(0, allocationPieSpacingDp(1))
        assertFalse(allocationPieInnerHole(1))
        assertEquals(0, allocationPieSpacingDp(0))
        assertFalse(allocationPieInnerHole(0))
    }

    @Test
    fun multipleSlicesKeepDonutGaps() {
        assertEquals(4, allocationPieSpacingDp(2))
        assertTrue(allocationPieInnerHole(2))
        assertEquals(4, allocationPieSpacingDp(3))
        assertTrue(allocationPieInnerHole(8))
    }
}
