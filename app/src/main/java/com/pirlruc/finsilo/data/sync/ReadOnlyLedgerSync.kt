package com.pirlruc.finsilo.data.sync

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.remote.OkHttpBrokerCall
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.importcsv.CoinbaseFillsClient
import com.pirlruc.finsilo.domain.importcsv.IbkrFlexClient
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.importcsv.KrakenReadClient
import com.pirlruc.finsilo.domain.importcsv.ReadOnlyPull
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Imports IBKR, Kraken, and Coinbase rows when those credentials are stored. */
class ReadOnlyLedgerSync(
    private val ibkr: IbkrFlexClient = IbkrFlexClient(OkHttpBrokerCall()),
    private val kraken: KrakenReadClient = KrakenReadClient(OkHttpBrokerCall()),
    private val coinbase: CoinbaseFillsClient = CoinbaseFillsClient(OkHttpBrokerCall()),
    private val importer: ImportBrokerCsvUseCase = ImportBrokerCsvUseCase(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    suspend fun run(keys: DatabaseKeyStore, repository: RoomPortfolioRepository): String? {
        val messages = listOfNotNull(
            pullIbkr(keys, repository),
            pullKraken(keys, repository),
            pullCoinbase(keys, repository),
        )
        return messages.joinToString(" ").ifBlank { null }
    }

    private suspend fun pullIbkr(keys: DatabaseKeyStore, repository: RoomPortfolioRepository): String? {
        val token = keys.ibkrToken() ?: return null
        val query = keys.ibkrQueryId() ?: return "IBKR token is stored, but the query id is missing."
        return import(repository, ibkr.pull(token, query))
    }

    private suspend fun pullKraken(keys: DatabaseKeyStore, repository: RoomPortfolioRepository): String? {
        val key = keys.krakenKey() ?: return null
        val secret = keys.krakenSecret() ?: return "Kraken key is stored, but the secret is missing."
        return import(repository, kraken.pull(key, secret, nowSeconds().toString()))
    }

    private suspend fun pullCoinbase(keys: DatabaseKeyStore, repository: RoomPortfolioRepository): String? {
        val name = keys.coinbaseKeyName() ?: return null
        val pem = keys.coinbaseKey() ?: return "Coinbase key name is stored, but the private key is missing."
        val key = privateKey(pem) ?: return "Coinbase private key is not a PKCS8 PEM."
        return import(repository, coinbase.pull(name, key, nowSeconds(), nowSeconds().toString()))
    }

    private suspend fun import(repository: RoomPortfolioRepository, pull: ReadOnlyPull): String? {
        val failed = pull.error ?: if (pull.lines == null) "Broker sync did not return rows." else null
        if (failed != null) return failed
        val before = repository.load()
        val imported = importer.importLines(before, pull.lines.orEmpty())
        if (imported.error == null) repository.persistImport(before, imported.snapshot)
        return imported.error
    }

    private fun privateKey(pem: String): java.security.PrivateKey? = runCatching {
        val body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))
        KeyFactory.getInstance("EC").generatePrivate(spec)
    }.getOrNull()
}
