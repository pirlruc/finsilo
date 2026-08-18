package com.pirlruc.finsilo.data.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.domain.model.NavPoint
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WidgetNavCacheTest {
    @Test
    fun roundTripAndClear() {
        val cache = WidgetNavCache(ApplicationProvider.getApplicationContext())
        val point = NavPoint(LocalDate.of(2026, 8, 16), BigDecimal("1234.56"))
        cache.write(point)
        val read = checkNotNull(cache.read())
        assertEquals(point.date, read.date)
        assertEquals(0, point.valueEur.compareTo(read.valueEur))
        cache.write(null)
        assertNull(cache.read())
    }
}
