package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Count matching broker rows so a second same-day lot is not treated as a duplicate. */
internal object ImportFingerprints {
    fun of(line: BrokerCsvLine): String {
        val date = line.date ?: return ""
        val type = line.type ?: return ""
        val id = line.isin?.ifBlank { null } ?: line.symbol
        return key(date, type, id, line.quantity, line.unitPriceNative, line.format.source)
    }

    fun counts(snapshot: PortfolioSnapshot): Map<String, Int> {
        val assets = snapshot.assets.associateBy { it.id }
        val counts = HashMap<String, Int>()
        for (tx in snapshot.transactions) {
            val fp = of(tx, assets[tx.assetId]?.isin, assets[tx.assetId]?.symbol)
            counts[fp] = (counts[fp] ?: 0) + 1
        }
        return counts
    }

    fun untaggedDeposit(line: BrokerCsvLine): String {
        val dated = line.copy(type = TransactionType.DEPOSIT_CASH)
        val date = dated.date ?: return ""
        val id = dated.isin?.ifBlank { null } ?: dated.symbol
        return key(date, TransactionType.DEPOSIT_CASH, id, dated.quantity, dated.unitPriceNative, null)
    }

    private fun of(tx: Transaction, isin: String?, symbol: String?): String {
        val id = isin?.ifBlank { null } ?: symbol ?: tx.assetId
        return key(tx.date, tx.type, id, tx.quantity, tx.unitPriceNative, tx.source)
    }

    private fun key(
        date: LocalDate,
        type: TransactionType,
        id: String,
        qty: BigDecimal,
        price: BigDecimal,
        source: BrokerSource?,
    ): String = listOf(
        date.toString(),
        type.name,
        id.uppercase(),
        qty.stripTrailingZeros().toPlainString(),
        price.stripTrailingZeros().toPlainString(),
        source?.name.orEmpty(),
    ).joinToString("|")
}
