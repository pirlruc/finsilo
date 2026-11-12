package com.pirlruc.finsilo.data.sync

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.remote.OkHttpTrading212Transport
import com.pirlruc.finsilo.data.remote.Trading212HistoryClient
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase

/** Pulls read-only history after unlock. A failed sync does not write the ledger. */
class Trading212LedgerSync(
    private val client: Trading212HistoryClient = Trading212HistoryClient(OkHttpTrading212Transport()),
    private val importer: ImportBrokerCsvUseCase = ImportBrokerCsvUseCase(),
) {
    suspend fun run(keys: DatabaseKeyStore, repository: RoomPortfolioRepository): String? {
        if (!keys.trading212SyncEnabled()) return null
        val missing = missingCredential(keys)
        return missing ?: importHistory(keys, repository)
    }

    private fun missingCredential(keys: DatabaseKeyStore): String? = when {
        keys.trading212Key() == null -> "Trading 212 sync is on, but the API key is missing."
        keys.trading212Secret() == null -> "Trading 212 sync is on, but the API secret is missing."
        else -> null
    }

    private suspend fun importHistory(keys: DatabaseKeyStore, repository: RoomPortfolioRepository): String? {
        val key = keys.trading212Key() ?: return null
        val secret = keys.trading212Secret() ?: return null
        val pull = client.pull(key, secret)
        val failed = pull.error ?: if (pull.lines == null) "Trading 212 sync did not return history." else null
        if (failed != null) return failed
        val before = repository.load()
        val imported = importer.importLines(before, pull.lines.orEmpty())
        if (imported.error == null) repository.persistImport(before, imported.snapshot)
        return imported.error
    }
}
