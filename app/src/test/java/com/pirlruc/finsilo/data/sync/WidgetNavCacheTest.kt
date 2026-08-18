package com.pirlruc.finsilo.data.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.domain.model.NavPoint
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("widget_nav_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val cache = WidgetNavCache(context, prefs)
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
        context.getSharedPreferences("finsilo_widget_nav", Context.MODE_PRIVATE)
            .edit()
            .putString("date", "2026-08-16")
            .putString("value_eur", "99.50")
            .commit()
        val dest = context.getSharedPreferences("widget_nav_secure_test", Context.MODE_PRIVATE)
        dest.edit().clear().commit()
        val cache = WidgetNavCache(context, dest)
        val read = checkNotNull(cache.read())
        assertEquals(LocalDate.of(2026, 8, 16), read.date)
        assertEquals(0, BigDecimal("99.50").compareTo(read.valueEur))
        val leftover = context.getSharedPreferences("finsilo_widget_nav", Context.MODE_PRIVATE)
        assertFalse(leftover.contains("date"))
        assertFalse(leftover.contains("value_eur"))
    }

    @Test
    fun productionWriteDoesNotCreatePlaintextFile() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("finsilo_widget_nav", Context.MODE_PRIVATE).edit().clear().commit()
        val cache = WidgetNavCache(context)
        cache.write(NavPoint(LocalDate.of(2026, 8, 16), BigDecimal("10.00")))
        val plain = context.getSharedPreferences("finsilo_widget_nav", Context.MODE_PRIVATE)
        assertFalse(plain.contains("date"))
        assertFalse(plain.contains("value_eur"))
    }
}
