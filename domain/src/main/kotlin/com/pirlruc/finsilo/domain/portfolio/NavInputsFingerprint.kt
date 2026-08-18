package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Content hash of the inputs that change total NAV. Collection sizes are not enough:
 * an FX/quote REPLACE with the same row count must still rebuild persisted history.
 */
object NavInputsFingerprint {
    fun of(snapshot: PortfolioSnapshot): String {
        val payload =
            buildString {
                snapshot.assets.sortedBy { it.id }.forEach { asset ->
                    append(asset.id).append('|')
                    append(asset.assetType).append('|')
                    append(asset.baseCurrency).append('|')
                    append(asset.quoteSymbol.orEmpty()).append('|')
                    append(asset.locallyValued).append(';')
                }
                append('#')
                snapshot.transactions.sortedBy { it.id }.forEach { tx ->
                    append(tx.id).append('|')
                    append(tx.date).append('|')
                    append(tx.type).append('|')
                    append(tx.assetId).append('|')
                    append(token(tx.quantity)).append('|')
                    append(token(tx.unitPriceEur)).append('|')
                    append(token(tx.feesEur)).append('|')
                    append(tx.sequence).append(';')
                }
                append('#')
                snapshot.fxRates.sortedBy { it.date }.forEach { rate ->
                    append(rate.date).append('|')
                    append(token(rate.eurPerUsd)).append(';')
                }
                append('#')
                snapshot.marketData.sortedWith(compareBy({ it.assetId }, { it.date })).forEach { row ->
                    append(row.assetId).append('|')
                    append(row.date).append('|')
                    append(token(row.closingPriceNative)).append(';')
                }
            }
        return sha256(payload)
    }

    private fun token(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
