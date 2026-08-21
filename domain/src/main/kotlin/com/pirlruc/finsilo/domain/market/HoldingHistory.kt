package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import java.time.LocalDate

/** First date a holding exists on the ledger — daily quotes start here, not at a rolling cap. */
object HoldingHistory {
    fun firstHeldOn(transactions: List<Transaction>, assetId: String): LocalDate? {
        var firstAny: LocalDate? = null
        var firstBuy: LocalDate? = null
        for (tx in transactions) {
            if (tx.assetId != assetId) continue
            if (firstAny == null || tx.date.isBefore(firstAny)) firstAny = tx.date
            if (tx.type == TransactionType.BUY && (firstBuy == null || tx.date.isBefore(firstBuy))) {
                firstBuy = tx.date
            }
        }
        return firstBuy ?: firstAny
    }
}
