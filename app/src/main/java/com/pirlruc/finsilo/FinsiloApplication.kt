package com.pirlruc.finsilo

import android.app.Application
import androidx.room.Room
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.remote.CompositeMarketFeed
import com.pirlruc.finsilo.data.security.AppLockStore
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.data.sync.DailyMarketSyncWorker
import com.pirlruc.finsilo.data.sync.WidgetNavCache
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class FinsiloApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        container = AppContainer(this)
        DailyMarketSyncWorker.schedule(this)
    }
}

class AppContainer(val application: Application) {
    private val keyStore = DatabaseKeyStore(application)
    val widgetNav = WidgetNavCache(application)

    val database: FinsiloDatabase =
        Room.databaseBuilder(application, FinsiloDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(keyStore.passphrase()))
            .addMigrations(FinsiloDatabase.MIGRATION_1_2)
            .build()

    val repository: RoomPortfolioRepository = RoomPortfolioRepository(database, widgetNav)
    val getDashboard: GetDashboardUseCase = GetDashboardUseCase()
    val marketFeed = CompositeMarketFeed(keys = keyStore)
    val keys: DatabaseKeyStore = keyStore
    val lockStore = AppLockStore(application)

    companion object {
        private const val DB_NAME = "finsilo.db"
    }
}
