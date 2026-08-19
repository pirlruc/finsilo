package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.TransactionType

internal data class LedgerInstrumentActions(
    val onNewInstrument: (Boolean) -> Unit,
    val onExistingAsset: (String?) -> Unit,
    val onSymbol: (String) -> Unit,
    val onName: (String) -> Unit,
    val onAssetType: (AssetType) -> Unit,
    val onCurrency: (Currency) -> Unit,
    val onIsin: (String) -> Unit,
    val onQuoteSymbol: (String) -> Unit,
)

internal data class LedgerAmountActions(
    val onType: (TransactionType) -> Unit,
    val onDate: (String) -> Unit,
    val onQuantity: (String) -> Unit,
    val onPrice: (String) -> Unit,
    val onFees: (String) -> Unit,
    val onFx: (String) -> Unit,
)

internal data class LedgerNavActions(
    val onClose: () -> Unit,
    val onSave: () -> Unit,
    val onApplyTemplate: (LedgerTemplate) -> Unit,
    val onSaveTemplate: (String) -> Unit,
    val onManualClose: (String) -> Unit,
    val onSaveManualClose: () -> Unit,
)

internal data class LedgerFormActions(val nav: LedgerNavActions, val amounts: LedgerAmountActions, val instrument: LedgerInstrumentActions)
