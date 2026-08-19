package com.pirlruc.finsilo.ui.ledger

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import java.util.UUID

internal object LedgerTemplateActions {
    val types: Set<TransactionType> =
        setOf(TransactionType.BUY, TransactionType.INTEREST, TransactionType.DEPOSIT_CASH)

    fun apply(state: LedgerUiState, snapshot: PortfolioSnapshot, template: LedgerTemplate): LedgerUiState {
        val assetExists = template.assetId != null && snapshot.assets.any { it.id == template.assetId }
        val status =
            if (template.assetId != null && !assetExists) {
                "Template “${template.label}” filled. That instrument is no longer in the ledger — pick one, then save."
            } else {
                "Template “${template.label}” filled. Save to post."
            }
        return state.copy(
            type = template.type,
            quantity = template.quantity,
            unitPriceNative = template.unitPriceNative,
            feesEur = template.feesEur,
            existingAssetId = if (assetExists) template.assetId else null,
            newInstrument = template.type == TransactionType.BUY && !assetExists,
            error = null,
            status = status,
        )
    }

    fun draft(state: LedgerUiState, label: String): LedgerTemplate = LedgerTemplate(
        id = UUID.randomUUID().toString(),
        label = label.ifBlank { state.type.name },
        type = state.type,
        assetId = state.existingAssetId,
        quantity = state.quantity,
        unitPriceNative = state.unitPriceNative,
        feesEur = state.feesEur,
    )

    suspend fun persist(repository: RoomPortfolioRepository, template: LedgerTemplate) {
        repository.saveTemplate(template)
    }
}
