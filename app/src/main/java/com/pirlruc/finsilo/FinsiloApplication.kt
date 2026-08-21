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
    val keys: DatabaseKeyStore = DatabaseKeyStore.create(application)
    val widgetNav = WidgetNavCache(application)
    val getDashboard: GetDashboardUseCase = GetDashboardUseCase()
    val marketFeed = CompositeMarketFeed(keys = keys)
    val lockStore = AppLockStore(application)

    @Volatile
    private var database: FinsiloDatabase? = null

    @Volatile
    private var portfolioRepository: RoomPortfolioRepository? = null

    val repository: RoomPortfolioRepository
        get() = portfolioRepository ?: error("Ledger is locked until PIN or recovery unwraps the database key.")

    fun isLedgerOpen(): Boolean = database != null

    @Synchronized
    fun openLedger() {
        if (database != null) return
        val db =
            Room.databaseBuilder(application, FinsiloDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(keys.sessionPassphrase()))
                .addMigrations(FinsiloDatabase.MIGRATION_1_2)
                .build()
        database = db
        portfolioRepository = RoomPortfolioRepository(db, widgetNav)
    }

    @Synchronized
    fun closeLedger() {
        val db = database
        database = null
        portfolioRepository = null
        db?.close()
        keys.evictSession()
    }

    companion object {
        private const val DB_NAME = "finsilo.db"
    }
}
