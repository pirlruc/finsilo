package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot

/** Tab-separated snapshot text inside the ciphertext. */
object LedgerBackupText {
    internal const val VERSION_1: String = "FSILO-LEDGER-1"
    internal const val VERSION_2: String = "FSILO-LEDGER-2"
    internal const val VERSION_3: String = "FSILO-LEDGER-3"

    /** Encode [snapshot] and extras as versioned TSV. */
    fun encode(snapshot: PortfolioSnapshot, extras: LedgerBackupExtras = LedgerBackupExtras()): String {
        val lines = ArrayList<String>()
        lines += VERSION_3
        appendSnapshot(lines, snapshot)
        appendExtras(lines, extras)
        lines += "END"
        return lines.joinToString("\n")
    }

    private fun appendSnapshot(lines: MutableList<String>, snapshot: PortfolioSnapshot) {
        snapshot.assets.forEach { lines += LedgerBackupEncode.asset(it) }
        snapshot.transactions.forEach { lines += LedgerBackupEncode.transaction(it) }
        snapshot.marketData.forEach { lines += LedgerBackupEncode.market(it) }
        snapshot.fxRates.forEach { lines += LedgerBackupEncode.fx(it) }
        snapshot.targets.forEach { lines += LedgerBackupEncode.target(it) }
    }

    private fun appendExtras(lines: MutableList<String>, extras: LedgerBackupExtras) {
        extras.watchlist.items.forEach { lines += LedgerBackupEncode.watchlistItem(it) }
        extras.watchlist.quotes.forEach { lines += LedgerBackupEncode.watchlistQuote(it) }
        extras.templates.forEach { lines += LedgerBackupEncode.template(it) }
        extras.thresholds.forEach { lines += LedgerBackupEncode.threshold(it) }
        extras.ratingAlerts.forEach { lines += LedgerBackupEncode.ratingAlert(it) }
    }

    /** Parse plaintext; refuse unknown versions and malformed rows. */
    fun decode(text: String): LedgerBackupResult {
        val lines = text.split('\n')
        val version = lines.firstOrNull()
        val extras = when (version) {
            VERSION_1 -> false
            VERSION_2, VERSION_3 -> true
            else -> return LedgerBackupResult.Refused("Unknown backup format.")
        }
        val ratings = version == VERSION_3
        return runCatching { LedgerBackupParse.parse(lines.drop(1), extras, ratings) }
            .getOrElse { LedgerBackupResult.Refused("Backup payload is malformed.") }
    }
}
