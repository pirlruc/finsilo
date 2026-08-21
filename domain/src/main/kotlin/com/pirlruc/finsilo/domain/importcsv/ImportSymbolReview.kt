package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType

/** One distinct marketable (or locally valued) instrument from a parsed CSV. */
data class ImportSymbolDraft(
    val key: String,
    val symbol: String,
    val quoteSymbol: String,
    val name: String,
    val assetType: AssetType,
    val currency: Currency,
    val isin: String?,
    val needsQuote: Boolean,
    val quoteWarning: String? = null,
) {
    fun asProbeAsset(): Asset = Asset(
        id = key,
        symbol = symbol,
        name = name,
        assetType = assetType,
        baseCurrency = currency,
        isin = isin,
        quoteSymbol = quoteSymbol.ifBlank { null },
    )
}

/** Unique instruments from broker rows, plus applying quote-symbol edits before persist. */
object ImportSymbolReview {
    private val holdingTypes =
        setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.DIVIDEND, TransactionType.INTEREST)

    fun drafts(lines: List<BrokerCsvLine>): List<ImportSymbolDraft> = lines.filter { it.type in holdingTypes && it.symbol.isNotBlank() }
        .groupBy { keyOf(it) }
        .values
        .map { group -> draftOf(group.first()) }
        .sortedBy { it.symbol }

    fun apply(lines: List<BrokerCsvLine>, drafts: List<ImportSymbolDraft>): List<BrokerCsvLine> {
        val byKey = drafts.associateBy { it.key }
        return lines.map { line ->
            val draft = byKey[keyOf(line)] ?: return@map line
            line.copy(symbol = draft.symbol, quoteSymbol = draft.quoteSymbol.ifBlank { null }, name = draft.name)
        }
    }

    fun keyOf(line: BrokerCsvLine): String {
        val isin = line.isin?.uppercase()?.takeIf { it.isNotBlank() }
        return isin ?: line.symbol.uppercase()
    }

    private fun draftOf(line: BrokerCsvLine): ImportSymbolDraft {
        val probe = Asset(
            id = "draft",
            symbol = line.symbol,
            name = line.name,
            assetType = line.assetType,
            baseCurrency = line.currency,
            isin = line.isin,
            quoteSymbol = line.quoteSymbol,
        )
        return ImportSymbolDraft(
            key = keyOf(line),
            symbol = line.symbol,
            quoteSymbol = line.quoteSymbol.orEmpty(),
            name = line.name.ifBlank { line.symbol },
            assetType = line.assetType,
            currency = line.currency,
            isin = line.isin,
            needsQuote = !probe.locallyValued,
        )
    }
}
