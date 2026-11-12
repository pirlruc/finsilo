package com.pirlruc.finsilo

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.room.Room
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.remote.CompositeMarketFeed
import com.pirlruc.finsilo.data.security.AppLockStore
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.data.security.LockoutSample
import com.pirlruc.finsilo.data.security.CredentialKeys
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.data.security.SecurePreferences
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

class AppContainer internal constructor(
    val application: Application,
    credentials: SharedPreferences,
    feedFactory: (DatabaseKeyStore) -> MarketFeed,
) {
    val keys: DatabaseKeyStore = DatabaseKeyStore(credentials)
    val widgetNav = WidgetNavCache(application)
    val getDashboard: GetDashboardUseCase = GetDashboardUseCase()
    val marketFeed: MarketFeed = feedFactory(keys)
    val lockStore = AppLockStore(credentials) {
        LockoutSample(
            wallMs = System.currentTimeMillis(),
            elapsedMs = SystemClock.elapsedRealtime(),
            bootCount = Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT, 0),
        )
    }

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
        // SQLCipher keeps this array for later connections. Do not zero it.
        val passphrase = keys.sessionPassphrase().copyOf()
        val db =
            Room.databaseBuilder(application, FinsiloDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(FinsiloDatabase.MIGRATION_1_2)
                .build()
        try {
            db.openHelper.writableDatabase
        } catch (error: Exception) {
            db.close()
            throw error
        }
        database = db
        portfolioRepository = RoomPortfolioRepository(db, widgetNav)
    }

    /** Production wiring: Keystore prefs and the GET-only composite feed. */
    constructor(application: Application) : this(
        application,
        SecurePreferences.open(application, CredentialKeys.FILE),
        { store -> CompositeMarketFeed(keys = store) },
    )

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
