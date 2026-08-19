package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.parseDate

internal data class LedgerFormSession(val snapshot: PortfolioSnapshot, val state: LedgerUiState)

internal object LedgerFormSessionFactory {
    suspend fun load(repository: RoomPortfolioRepository, current: LedgerUiState, resetForm: Boolean): LedgerFormSession {
        val snapshot = repository.load()
        val templates = repository.loadTemplates()
        val fx = snapshot.fxRates.maxByOrNull { rate -> rate.date }?.eurPerUsd?.toPlainString().orEmpty()
        val assets = snapshot.assets.filter { asset -> asset.assetType != AssetType.CASH }
        val state =
            if (resetForm) {
                LedgerUiState(loading = false, assets = assets, eurPerUsd = fx, templates = templates)
            } else {
                current.copy(
                    loading = false,
                    assets = assets,
                    eurPerUsd = current.eurPerUsd.ifBlank { fx },
                    templates = templates,
                )
            }
        return LedgerFormSession(snapshot, withHints(snapshot, state, PositionLedger()))
    }

    fun reset(snapshot: PortfolioSnapshot, state: LedgerUiState, status: String, ledger: PositionLedger): LedgerUiState {
        val next =
            LedgerUiState(
                loading = false,
                saving = false,
                status = status,
                assets = state.assets,
                templates = state.templates,
                eurPerUsd = state.eurPerUsd,
            )
        return withHints(snapshot, next, ledger)
    }

    fun withHints(snapshot: PortfolioSnapshot, state: LedgerUiState, ledger: PositionLedger): LedgerUiState {
        val asOf = parseDate(state.date)
        val prior = if (asOf == null) snapshot.transactions else ledger.transactionsOnOrBefore(snapshot.transactions, asOf)
        val cash = ledger.cashEur(prior, snapshot.assets.associateBy { it.id })
        val asset = snapshot.assets.firstOrNull { it.id == state.existingAssetId }
        val remaining =
            if (asset == null || state.type != TransactionType.SELL) {
                null
            } else {
                ledger.position(prior.filter { it.assetId == asset.id }).quantity.stripTrailingZeros().toPlainString()
            }
        return state.copy(remainingQty = remaining, cashEur = cash.stripTrailingZeros().toPlainString())
    }
}
