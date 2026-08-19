package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.usecase.LedgerEntryRequest
import com.pirlruc.finsilo.domain.usecase.NewAssetDraft
import com.pirlruc.finsilo.domain.usecase.parseDate
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import java.math.BigDecimal

internal object LedgerFormMapper {
    fun toRequest(state: LedgerUiState): LedgerEntryRequest? {
        val date = parseDate(state.date) ?: return null
        val quantity = quantityFor(state) ?: return null
        val price = priceFor(state) ?: return null
        return LedgerEntryRequest(
            type = state.type,
            date = date,
            quantity = quantity,
            unitPriceNative = price,
            feesEur = feesFor(state),
            existingAssetId = existingAssetIdFor(state),
            newAsset = newAssetFor(state),
            eurPerUsd = parseDecimal(state.eurPerUsd),
        )
    }

    fun selectableAssets(state: LedgerUiState): List<Asset> = when (state.type) {
        TransactionType.INTEREST -> state.assets.filter { it.assetType.allowsInterest }
        TransactionType.DIVIDEND -> state.assets.filter { it.assetType.allowsDividend }
        TransactionType.SELL, TransactionType.BUY -> state.assets
        else -> emptyList()
    }

    fun selectedAsset(state: LedgerUiState): Asset? = state.assets.firstOrNull { it.id == state.existingAssetId }

    fun selectedCurrency(state: LedgerUiState): Currency {
        if (state.type == TransactionType.BUY && state.newInstrument) return state.currency
        return selectedAsset(state)?.baseCurrency ?: Currency.EUR
    }

    fun selectedType(state: LedgerUiState): AssetType {
        if (state.type == TransactionType.BUY && state.newInstrument) return state.assetType
        return selectedAsset(state)?.assetType ?: state.assetType
    }

    fun needsFxField(state: LedgerUiState): Boolean {
        if (selectedCurrency(state) == Currency.USD) return true
        val type = selectedType(state)
        if (type == AssetType.CRYPTO || type == AssetType.COMMODITY) return true
        val asset = selectedAsset(state) ?: return false
        return QuoteCurrency.needsUsdFx(asset)
    }

    fun isLocallyValuedSelection(state: LedgerUiState): Boolean {
        if (state.type == TransactionType.BUY && state.newInstrument) {
            return state.assetType.isLocallyValued
        }
        return selectedAsset(state)?.locallyValued == true
    }

    private fun quantityFor(state: LedgerUiState): BigDecimal? =
        if (state.type == TransactionType.INTEREST) BigDecimal.ONE else parseDecimal(state.quantity)

    private fun priceFor(state: LedgerUiState): BigDecimal? =
        if (state.type == TransactionType.DEPOSIT_CASH || state.type == TransactionType.WITHDRAWAL) {
            BigDecimal.ONE
        } else {
            parseDecimal(state.unitPriceNative)
        }

    private fun feesFor(state: LedgerUiState): BigDecimal {
        val cashLike = state.type == TransactionType.DEPOSIT_CASH ||
            state.type == TransactionType.WITHDRAWAL ||
            state.type == TransactionType.INTEREST
        return if (cashLike) BigDecimal.ZERO else parseDecimal(state.feesEur) ?: BigDecimal.ZERO
    }

    private fun existingAssetIdFor(state: LedgerUiState): String? =
        if (state.newInstrument && state.type == TransactionType.BUY) null else state.existingAssetId

    private fun newAssetFor(state: LedgerUiState): NewAssetDraft? {
        if (state.type != TransactionType.BUY || !state.newInstrument) return null
        return NewAssetDraft(
            symbol = state.symbol,
            name = state.name,
            assetType = state.assetType,
            baseCurrency = state.currency,
            isin = state.isin.ifBlank { null },
            quoteSymbol = state.quoteSymbol.ifBlank { null },
        )
    }
}

internal fun TransactionType.label(): String = when (this) {
    TransactionType.BUY -> "Buy"
    TransactionType.SELL -> "Sell"
    TransactionType.DEPOSIT_CASH -> "Deposit"
    TransactionType.WITHDRAWAL -> "Withdrawal"
    TransactionType.DIVIDEND -> "Dividend"
    TransactionType.INTEREST -> "Interest"
}
