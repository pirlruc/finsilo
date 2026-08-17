package com.pirlruc.finsilo

import android.app.Application
import androidx.room.Room
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.remote.CompositeMarketFeed
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.data.sync.DailyMarketSyncWorker
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

class AppContainer(application: Application) {
    private val keyStore = DatabaseKeyStore(application)

    val database: FinsiloDatabase =
        Room.databaseBuilder(application, FinsiloDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(keyStore.passphrase()))
            .addMigrations(
                FinsiloDatabase.MIGRATION_1_2,
                FinsiloDatabase.MIGRATION_2_3,
                FinsiloDatabase.MIGRATION_3_4,
            )
            .build()

    val repository: RoomPortfolioRepository = RoomPortfolioRepository(database)
    val getDashboard: GetDashboardUseCase = GetDashboardUseCase()
    val marketFeed = CompositeMarketFeed(keys = keyStore)
    val keys: DatabaseKeyStore = keyStore

    companion object {
        private const val DB_NAME = "finsilo.db"
    }
}
