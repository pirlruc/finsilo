package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.usecase.LedgerEntryResult
import com.pirlruc.finsilo.domain.usecase.ManualQuoteResult
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import com.pirlruc.finsilo.domain.usecase.RecordManualQuoteUseCase
import com.pirlruc.finsilo.domain.usecase.parseDate
import com.pirlruc.finsilo.domain.usecase.parseDecimal

internal sealed class LedgerSaveOutcome {
    data class StateOnly(val state: LedgerUiState) : LedgerSaveOutcome()

    data class ManualSaved(val state: LedgerUiState, val snapshot: PortfolioSnapshot) : LedgerSaveOutcome()

    data class Posted(val status: String, val assetId: String) : LedgerSaveOutcome()
}

internal object LedgerSaveActions {
    suspend fun saveManualClose(
        repository: RoomPortfolioRepository,
        manualQuote: RecordManualQuoteUseCase,
        snapshot: PortfolioSnapshot,
        state: LedgerUiState,
    ): LedgerSaveOutcome {
        val date = parseDate(state.date)
        val price = parseDecimal(state.manualClose)
        val assetId = state.existingAssetId
        if (date == null || price == null || assetId == null) {
            return LedgerSaveOutcome.StateOnly(
                state.copy(error = "Pick an existing locally valued instrument, date, and statement close."),
            )
        }
        return when (val result = manualQuote(snapshot, assetId, date, price)) {
            is ManualQuoteResult.Rejected -> LedgerSaveOutcome.StateOnly(state.copy(error = result.reason))
            is ManualQuoteResult.Accepted -> persistManual(repository, state, result)
        }
    }

    suspend fun saveEntry(
        repository: RoomPortfolioRepository,
        record: RecordLedgerEntryUseCase,
        snapshot: PortfolioSnapshot,
        state: LedgerUiState,
    ): LedgerSaveOutcome {
        val busy = state.copy(saving = true, error = null, status = null)
        val request = LedgerFormMapper.toRequest(busy)
        if (request == null) {
            return LedgerSaveOutcome.StateOnly(
                busy.copy(saving = false, error = "Fill date, quantity, and price with valid numbers."),
            )
        }
        return when (val result = record(snapshot, request, fundBuyWithDeposit = request.type == TransactionType.BUY)) {
            is LedgerEntryResult.Rejected -> LedgerSaveOutcome.StateOnly(busy.copy(saving = false, error = result.reason))
            is LedgerEntryResult.Accepted -> writeAccepted(repository, snapshot, busy, result)
        }
    }

    private suspend fun persistManual(
        repository: RoomPortfolioRepository,
        state: LedgerUiState,
        result: ManualQuoteResult.Accepted,
    ): LedgerSaveOutcome = runCatching { repository.upsertQuotes(listOf(result.row), emptyList()) }
        .fold(
            onSuccess = {
                LedgerSaveOutcome.ManualSaved(
                    state.copy(status = "Saved statement close (not a transaction).", error = null, manualClose = ""),
                    repository.load(),
                )
            },
            onFailure = { error ->
                LedgerSaveOutcome.StateOnly(state.copy(error = error.message ?: "Could not save close"))
            },
        )

    private suspend fun writeAccepted(
        repository: RoomPortfolioRepository,
        snapshot: PortfolioSnapshot,
        state: LedgerUiState,
        result: LedgerEntryResult.Accepted,
    ): LedgerSaveOutcome = runCatching {
        repository.persistImport(snapshot, withAccepted(snapshot, result))
    }.fold(
        onSuccess = { LedgerSaveOutcome.Posted(postedStatus(result), result.asset.id) },
        onFailure = { error ->
            LedgerSaveOutcome.StateOnly(state.copy(saving = false, error = error.message ?: "Could not save"))
        },
    )

    private fun withAccepted(snapshot: PortfolioSnapshot, result: LedgerEntryResult.Accepted): PortfolioSnapshot {
        val extraAssets = listOfNotNull(result.fundingCashAsset, result.asset.takeIf { result.createdAsset })
        val extraTx = listOfNotNull(result.fundingDeposit, result.transaction)
        val fx = result.fxRate?.let { rate ->
            if (snapshot.fxRates.any { it.date == rate.date }) snapshot.fxRates else snapshot.fxRates + rate
        } ?: snapshot.fxRates
        return snapshot.copy(
            assets = snapshot.assets + extraAssets,
            transactions = snapshot.transactions + extraTx,
            fxRates = fx,
        )
    }

    private fun postedStatus(result: LedgerEntryResult.Accepted): String {
        val deposit = result.fundingDeposit ?: return "Saved ${result.transaction.type.name.lowercase()}"
        val euros = deposit.notionalEur.stripTrailingZeros().toPlainString()
        return "Saved buy and a matching $euros EUR cash deposit."
    }
}
