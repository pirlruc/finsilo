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
import com.pirlruc.finsilo.data.security.CredentialKeys
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.data.security.LockoutSample
import com.pirlruc.finsilo.data.security.SecurePreferences
import com.pirlruc.finsilo.data.security.commitObserved
import com.pirlruc.finsilo.data.sync.DailyMarketSyncWorker
import com.pirlruc.finsilo.data.sync.WidgetNavCache
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import com.pirlruc.finsilo.domain.vault.VaultRecord
import com.pirlruc.finsilo.domain.vault.VaultRegistry
import com.pirlruc.finsilo.domain.vault.VaultSelection
import java.io.File
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
    private val credentials: SharedPreferences,
    feedFactory: (DatabaseKeyStore) -> MarketFeed,
) {
    val vaultSelection = VaultSelection()
    val keys: DatabaseKeyStore = DatabaseKeyStore(credentials, vaultSelection)
    val widgetNav = WidgetNavCache(application)
    val getDashboard: GetDashboardUseCase = GetDashboardUseCase()
    val marketFeed: MarketFeed = feedFactory(keys)
    val lockStore = AppLockStore(credentials, vaultSelection) {
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

    init {
        val saved = credentials.getString(ACTIVE_VAULT, null)
        if (!saved.isNullOrBlank() && saved != VaultRegistry.DEFAULT_ID && VaultRegistry.contains(vaultRecords(), saved)) {
            vaultSelection.id = saved
        }
    }

    fun isLedgerOpen(): Boolean = database != null

    fun ledgerFileName(): String = VaultRegistry.fileName(vaultSelection.id)

    fun vaultRecords(): List<VaultRecord> = VaultDirectory(databaseDirectory()).read()

    fun createVault(label: String): VaultRecord? = VaultDirectory(databaseDirectory()).create(label)

    @Synchronized
    fun switchVault(id: String): Boolean {
        if (!VaultRegistry.contains(vaultRecords(), id)) return false
        if (id == vaultSelection.id) return true
        closeLedger()
        vaultSelection.id = id
        return credentials.edit().putString(ACTIVE_VAULT, id).commitObserved()
    }

    @Synchronized
    fun openLedger() {
        if (database != null) return
        // SQLCipher keeps this array for later connections. Do not zero it.
        val passphrase = keys.sessionPassphrase().copyOf()
        val db =
            Room.databaseBuilder(application, FinsiloDatabase::class.java, ledgerFileName())
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

    private fun databaseDirectory(): File {
        val path = application.getDatabasePath(VaultRegistry.LEGACY_FILE)
        return path.parentFile ?: application.filesDir
    }

    companion object {
        private const val ACTIVE_VAULT = "active_vault"
    }
}

/** Registry file beside the databases. Reading a missing file does not move finsilo.db. */
class VaultDirectory(private val directory: File) {
    fun read(): List<VaultRecord> {
        val file = File(directory, VaultRegistry.REGISTRY_FILE)
        if (!file.isFile) return VaultRegistry.missing()
        return VaultRegistry.decode(file.readText())
    }

    fun create(label: String): VaultRecord? {
        val current = read()
        val created = VaultRegistry.add(current, label) ?: return null
        directory.mkdirs()
        File(directory, VaultRegistry.REGISTRY_FILE).writeText(VaultRegistry.encode(current + created))
        return created
    }
}
