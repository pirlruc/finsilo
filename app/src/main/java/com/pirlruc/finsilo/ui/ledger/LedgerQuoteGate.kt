package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.TransactionType

internal object LedgerQuoteGate {
    fun probeAsset(state: LedgerUiState): Asset? {
        if (state.type != TransactionType.BUY || !state.newInstrument) return null
        val symbol = state.symbol.trim()
        if (symbol.isEmpty()) return null
        val asset =
            Asset(
                id = "probe",
                symbol = symbol,
                name = state.name.trim().ifBlank { symbol },
                assetType = state.assetType,
                baseCurrency = state.currency,
                isin = state.isin.trim().ifBlank { null },
                quoteSymbol = state.quoteSymbol.trim().ifBlank { null },
            )
        return if (asset.locallyValued) null else asset
    }
}
