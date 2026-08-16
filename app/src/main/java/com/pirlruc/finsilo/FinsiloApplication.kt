package com.pirlruc.finsilo

import android.app.Application
import androidx.room.Room
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class FinsiloApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val keyStore = DatabaseKeyStore(application)

    val database: FinsiloDatabase =
        Room.databaseBuilder(application, FinsiloDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(keyStore.passphrase()))
            .build()

    val repository: RoomPortfolioRepository = RoomPortfolioRepository(database)
    val getDashboard: GetDashboardUseCase = GetDashboardUseCase()

    companion object {
        private const val DB_NAME = "finsilo.db"
    }
}
