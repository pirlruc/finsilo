package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.WatchlistItem

internal object LedgerBackupEncode {
    fun asset(asset: Asset): String = listOf(
        "A",
        asset.id,
        esc(asset.symbol),
        esc(asset.name),
        asset.assetType.name,
        asset.baseCurrency.name,
        esc(asset.isin.orEmpty()),
        esc(asset.quoteSymbol.orEmpty()),
    ).joinToString("\t")

    fun transaction(tx: Transaction): String = listOf(
        "T",
        tx.id,
        tx.assetId,
        tx.date.toString(),
        tx.type.name,
        tx.quantity.toPlainString(),
        tx.unitPriceNative.toPlainString(),
        tx.exchangeRateAtExecution.toPlainString(),
        tx.unitPriceEur.toPlainString(),
        tx.feesEur.toPlainString(),
        tx.sequence.toString(),
    ).joinToString("\t")

    fun market(row: DailyMarketData): String = listOf(
        "M",
        row.assetId,
        row.date.toString(),
        row.closingPriceNative.toPlainString(),
        row.analystRating.name,
        row.sma50?.toPlainString().orEmpty(),
        row.sma200?.toPlainString().orEmpty(),
    ).joinToString("\t")

    fun fx(rate: CurrencyRate): String = listOf("X", rate.date.toString(), rate.eurPerUsd.toPlainString()).joinToString("\t")

    fun target(target: TargetAllocation): String =
        listOf("G", target.assetType.name, target.weightPercent.toPlainString()).joinToString("\t")

    fun watchlistItem(item: WatchlistItem): String = listOf(
        "W",
        item.id,
        esc(item.symbol),
        esc(item.name),
        item.assetType.name,
        item.baseCurrency.name,
        esc(item.quoteSymbol.orEmpty()),
    ).joinToString("\t")

    fun watchlistQuote(row: DailyMarketData): String = listOf(
        "Q",
        row.assetId,
        row.date.toString(),
        row.closingPriceNative.toPlainString(),
        row.analystRating.name,
        row.sma50?.toPlainString().orEmpty(),
        row.sma200?.toPlainString().orEmpty(),
    ).joinToString("\t")

    fun template(row: LedgerTemplate): String = listOf(
        "L",
        row.id,
        esc(row.label),
        row.type.name,
        row.assetId.orEmpty(),
        esc(row.quantity),
        esc(row.unitPriceNative),
        esc(row.feesEur),
    ).joinToString("\t")

    fun threshold(row: PriceAlertThreshold): String = listOf(
        "H",
        row.assetId,
        row.eurLevel?.toPlainString().orEmpty(),
        row.percentMove?.toPlainString().orEmpty(),
    ).joinToString("\t")

    fun esc(value: String): String = value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
}
