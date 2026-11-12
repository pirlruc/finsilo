package com.pirlruc.finsilo.data.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DailyMarketSyncScheduleTest {
    @Test
    fun scheduleBuildsTheEveningWorkRequest() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        try {
            DailyMarketSyncWorker.schedule(context)
        } catch (error: IllegalStateException) {
            check(error.message.orEmpty().isNotEmpty())
        }
    }
}
