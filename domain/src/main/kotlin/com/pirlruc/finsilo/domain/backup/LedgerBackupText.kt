package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot

/** Tab-separated snapshot text inside the ciphertext. */
object LedgerBackupText {
    internal const val VERSION_1: String = "FSILO-LEDGER-1"
    internal const val VERSION_2: String = "FSILO-LEDGER-2"

    /** Encode [snapshot] and Room v6 [extras] as versioned TSV. */
    fun encode(snapshot: PortfolioSnapshot, extras: LedgerBackupExtras = LedgerBackupExtras()): String {
        val lines = ArrayList<String>()
        lines += VERSION_2
        snapshot.assets.forEach { lines += LedgerBackupEncode.asset(it) }
        snapshot.transactions.forEach { lines += LedgerBackupEncode.transaction(it) }
        snapshot.marketData.forEach { lines += LedgerBackupEncode.market(it) }
        snapshot.fxRates.forEach { lines += LedgerBackupEncode.fx(it) }
        snapshot.targets.forEach { lines += LedgerBackupEncode.target(it) }
        extras.watchlist.items.forEach { lines += LedgerBackupEncode.watchlistItem(it) }
        extras.watchlist.quotes.forEach { lines += LedgerBackupEncode.watchlistQuote(it) }
        extras.templates.forEach { lines += LedgerBackupEncode.template(it) }
        extras.thresholds.forEach { lines += LedgerBackupEncode.threshold(it) }
        lines += "END"
        return lines.joinToString("\n")
    }

    /** Parse plaintext; refuse unknown versions and malformed rows. */
    fun decode(text: String): LedgerBackupResult {
        val lines = text.split('\n')
        val version = lines.firstOrNull()
        if (version != VERSION_1 && version != VERSION_2) {
            return LedgerBackupResult.Refused("Unknown backup format.")
        }
        return runCatching { LedgerBackupParse.parse(lines.drop(1), version == VERSION_2) }
            .getOrElse { LedgerBackupResult.Refused("Backup payload is malformed.") }
    }
}
