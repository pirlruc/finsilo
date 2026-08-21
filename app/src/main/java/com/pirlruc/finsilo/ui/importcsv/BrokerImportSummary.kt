package com.pirlruc.finsilo.ui.importcsv

import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvResult
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot

internal fun matchingAsset(snapshot: PortfolioSnapshot, draft: ImportSymbolDraft): Asset? {
    val isin = draft.isin?.uppercase()
    if (isin != null) {
        snapshot.assets.firstOrNull { it.isin?.uppercase() == isin }?.let { return it }
    }
    return snapshot.assets.firstOrNull { it.symbol.equals(draft.symbol, ignoreCase = true) }
}

internal fun importStatus(result: ImportBrokerCsvResult, drafts: List<ImportSymbolDraft>): String {
    val warned = drafts.filter { it.quoteWarning != null }.map { it.symbol }
    val warning =
        if (warned.isEmpty()) {
            ""
        } else {
            " No live quote for ${warned.take(3).joinToString()}${if (warned.size > 3) "…" else ""}."
        }
    return brokerImportSummary(result) + warning
}

internal fun brokerImportSummary(result: ImportBrokerCsvResult): String {
    val skips =
        if (result.skipped.isEmpty()) {
            ""
        } else {
            " Skipped ${result.skipped.size}: ${result.skipped.take(3).joinToString("; ")}."
        }
    val dups = if (result.duplicates == 0) "" else " ${result.duplicates} duplicate(s)."
    val funded = if (result.fundedDeposits == 0) "" else " ${result.fundedDeposits} cash top-up(s) to fund buys."
    return "Imported ${result.accepted} row(s).$funded$dups$skips"
}
