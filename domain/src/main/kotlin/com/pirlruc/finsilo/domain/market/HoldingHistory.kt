package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import java.time.LocalDate

/** First date a holding exists on the ledger — daily quotes start here, not at a rolling cap. */
object HoldingHistory {
    fun firstHeldOn(transactions: List<Transaction>, assetId: String): LocalDate? {
        val mine = transactions.filter { it.assetId == assetId }
        if (mine.isEmpty()) return null
        return mine.filter { it.type == TransactionType.BUY }.minOfOrNull { it.date }
            ?: mine.minOfOrNull { it.date }
    }
}
