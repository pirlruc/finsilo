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

    @Test
    fun migratesLegacyPlaintextPrefs() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("finsilo_widget_nav", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("date", "2026-08-16")
            .putString("value_eur", "99.50")
            .commit()
        val cache = WidgetNavCache(context)
        val read = checkNotNull(cache.read())
        assertEquals(LocalDate.of(2026, 8, 16), read.date)
        assertEquals(0, BigDecimal("99.50").compareTo(read.valueEur))
    }
}
