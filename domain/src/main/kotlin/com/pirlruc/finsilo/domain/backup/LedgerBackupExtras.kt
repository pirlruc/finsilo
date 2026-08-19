package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot

/** Watchlist, templates, and price alerts stored beside the live ledger. */
data class LedgerBackupExtras(
    val watchlist: WatchlistSnapshot = WatchlistSnapshot(),
    val templates: List<LedgerTemplate> = emptyList(),
    val thresholds: List<PriceAlertThreshold> = emptyList(),
)

/** Outcome of opening an on-device backup file. */
sealed class LedgerBackupResult {
    /** Decrypted ledger plus Room v6 extras. */
    data class Restored(val snapshot: PortfolioSnapshot, val extras: LedgerBackupExtras = LedgerBackupExtras()) : LedgerBackupResult()

    /** Truncated, corrupt, or wrong passphrase. */
    data class Refused(val reason: String) : LedgerBackupResult()
}
