package com.pirlruc.finsilo.domain.importcsv

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
        return key(date, type, id, line.quantity, line.unitPriceNative)
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

    private fun of(tx: Transaction, isin: String?, symbol: String?): String {
        val id = isin?.ifBlank { null } ?: symbol ?: tx.assetId
        return key(tx.date, tx.type, id, tx.quantity, tx.unitPriceNative)
    }

    private fun key(date: LocalDate, type: TransactionType, id: String, qty: BigDecimal, price: BigDecimal): String = listOf(
        date.toString(),
        type.name,
        id.uppercase(),
        qty.stripTrailingZeros().toPlainString(),
        price.stripTrailingZeros().toPlainString(),
    ).joinToString("|")
}
