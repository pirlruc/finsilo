package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import java.math.BigDecimal
import java.time.LocalDate

internal object LedgerBackupParse {
    private val v1Kinds = setOf("A", "T", "M", "X", "G")
    private val v2Kinds = v1Kinds + setOf("W", "Q", "L", "H")

    fun parse(lines: List<String>, extrasEnabled: Boolean): LedgerBackupResult {
        val kinds = if (extrasEnabled) v2Kinds else v1Kinds
        val unknown = lines.firstOrNull { row -> unknownRow(row, kinds) }
        if (unknown != null) return LedgerBackupResult.Refused("Unknown backup row.")
        val buckets = ParseBuckets()
        lines.filter { it.isNotEmpty() && it != "END" }.forEach { line ->
            buckets.append(line.split('\t'))
        }
        return LedgerBackupResult.Restored(buckets.snapshot(), buckets.extras())
    }

    private fun unknownRow(line: String, kinds: Set<String>): Boolean {
        if (line.isEmpty() || line == "END") return false
        return line.substringBefore('\t') !in kinds
    }

    private class ParseBuckets {
        val assets = ArrayList<Asset>()
        val txs = ArrayList<Transaction>()
        val market = ArrayList<DailyMarketData>()
        val fx = ArrayList<CurrencyRate>()
        val targets = ArrayList<TargetAllocation>()
        val watchItems = ArrayList<WatchlistItem>()
        val watchQuotes = ArrayList<DailyMarketData>()
        val templates = ArrayList<LedgerTemplate>()
        val thresholds = ArrayList<PriceAlertThreshold>()

        fun append(cols: List<String>) {
            when (cols.firstOrNull()) {
                "A" -> assets += parseAsset(cols)
                "T" -> txs += parseTx(cols)
                "M" -> market += parseMarket(cols)
                "X" -> fx += parseFx(cols)
                "G" -> targets += parseTarget(cols)
                "W" -> watchItems += parseWatchItem(cols)
                "Q" -> watchQuotes += parseMarket(cols)
                "L" -> templates += parseTemplate(cols)
                "H" -> thresholds += parseThreshold(cols)
            }
        }

        fun snapshot() = PortfolioSnapshot(assets, txs, market, fx, targets)

        fun extras() = LedgerBackupExtras(WatchlistSnapshot(watchItems, watchQuotes), templates, thresholds)
    }

    private fun parseAsset(cols: List<String>): Asset {
        require(cols.size >= 8)
        return Asset(
            id = cols[1],
            symbol = unesc(cols[2]),
            name = unesc(cols[3]),
            assetType = AssetType.valueOf(cols[4]),
            baseCurrency = Currency.valueOf(cols[5]),
            isin = unesc(cols[6]).ifBlank { null },
            quoteSymbol = unesc(cols[7]).ifBlank { null },
        )
    }

    private fun parseTx(cols: List<String>): Transaction {
        require(cols.size >= 11)
        return Transaction(
            id = cols[1],
            assetId = cols[2],
            date = LocalDate.parse(cols[3]),
            type = TransactionType.valueOf(cols[4]),
            quantity = BigDecimal(cols[5]),
            unitPriceNative = BigDecimal(cols[6]),
            exchangeRateAtExecution = BigDecimal(cols[7]),
            unitPriceEur = BigDecimal(cols[8]),
            feesEur = BigDecimal(cols[9]),
            sequence = cols[10].toLong(),
        )
    }

    private fun parseMarket(cols: List<String>): DailyMarketData {
        require(cols.size >= 7)
        return DailyMarketData(
            assetId = cols[1],
            date = LocalDate.parse(cols[2]),
            closingPriceNative = BigDecimal(cols[3]),
            analystRating = AnalystRating.valueOf(cols[4]),
            sma50 = cols[5].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
            sma200 = cols[6].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
        )
    }

    private fun parseFx(cols: List<String>): CurrencyRate {
        require(cols.size >= 3)
        return CurrencyRate(LocalDate.parse(cols[1]), BigDecimal(cols[2]))
    }

    private fun parseTarget(cols: List<String>): TargetAllocation {
        require(cols.size >= 3)
        return TargetAllocation(AssetType.valueOf(cols[1]), BigDecimal(cols[2]))
    }

    private fun parseWatchItem(cols: List<String>): WatchlistItem {
        require(cols.size >= 7)
        return WatchlistItem(
            id = cols[1],
            symbol = unesc(cols[2]),
            name = unesc(cols[3]),
            assetType = AssetType.valueOf(cols[4]),
            baseCurrency = Currency.valueOf(cols[5]),
            quoteSymbol = unesc(cols[6]).ifBlank { null },
        )
    }

    private fun parseTemplate(cols: List<String>): LedgerTemplate {
        require(cols.size >= 8)
        return LedgerTemplate(
            id = cols[1],
            label = unesc(cols[2]),
            type = TransactionType.valueOf(cols[3]),
            assetId = cols[4].ifBlank { null },
            quantity = unesc(cols[5]),
            unitPriceNative = unesc(cols[6]),
            feesEur = unesc(cols[7]),
        )
    }

    private fun parseThreshold(cols: List<String>): PriceAlertThreshold {
        require(cols.size >= 4)
        return PriceAlertThreshold(
            assetId = cols[1],
            eurLevel = cols[2].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
            percentMove = cols[3].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
        )
    }

    private fun unesc(value: String): String = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
