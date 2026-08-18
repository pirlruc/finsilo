package com.pirlruc.finsilo.ui.watchlist

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency

internal data class WatchlistActions(
    val onClose: () -> Unit,
    val onSymbol: (String) -> Unit,
    val onName: (String) -> Unit,
    val onAssetType: (AssetType) -> Unit,
    val onCurrency: (Currency) -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (String) -> Unit,
    val onSync: () -> Unit,
)
