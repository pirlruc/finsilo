package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import java.time.LocalDate

data class LedgerUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val status: String? = null,
    val type: TransactionType = TransactionType.BUY,
    val date: String = LocalDate.now().toString(),
    val quantity: String = "",
    val unitPriceNative: String = "",
    val feesEur: String = "0",
    val eurPerUsd: String = "",
    val existingAssetId: String? = null,
    val newInstrument: Boolean = true,
    val symbol: String = "",
    val name: String = "",
    val assetType: AssetType = AssetType.STOCK,
    val currency: Currency = Currency.EUR,
    val isin: String = "",
    val quoteSymbol: String = "",
    val assets: List<Asset> = emptyList(),
    val cashEur: String? = null,
    val remainingQty: String? = null,
)
