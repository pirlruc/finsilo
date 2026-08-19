package com.pirlruc.finsilo.domain.model

/**
 * User-defined ledger pre-fill. Saving a template must not write a [Transaction].
 * Only [TransactionType.BUY], [TransactionType.INTEREST], and
 * [TransactionType.DEPOSIT_CASH] are supported.
 */
data class LedgerTemplate(
    val id: String,
    val label: String,
    val type: TransactionType,
    val assetId: String? = null,
    val quantity: String = "",
    val unitPriceNative: String = "",
    val feesEur: String = "0",
)
