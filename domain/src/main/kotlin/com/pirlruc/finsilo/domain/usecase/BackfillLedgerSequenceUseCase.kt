package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.portfolio.PositionLedger

/**
 * Assigns unique positive [Transaction.sequence] values in current replay order
 * so migrated `sequence = 0` rows stop tie-breaking on UUID text.
 */
class BackfillLedgerSequenceUseCase(private val ledger: PositionLedger = PositionLedger()) {
    operator fun invoke(transactions: List<Transaction>): List<Transaction> {
        if (transactions.isEmpty() || alreadyUniquePositive(transactions)) return transactions
        return ledger.ordered(transactions).mapIndexed { index, tx -> tx.copy(sequence = index + 1L) }
    }

    private fun alreadyUniquePositive(transactions: List<Transaction>): Boolean {
        if (transactions.any { it.sequence <= 0L }) return false
        return transactions.map { it.sequence }.toHashSet().size == transactions.size
    }
}
