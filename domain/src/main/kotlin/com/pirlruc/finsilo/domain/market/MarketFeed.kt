package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import java.math.BigDecimal
import java.time.LocalDate

interface MarketFeed {
    suspend fun eurPerUsd(): BigDecimal

    suspend fun dailyHistory(asset: Asset): List<PriceBar>

    suspend fun analystRating(asset: Asset): AnalystRating
}

object MovingAverages {
    fun sma(closes: List<BigDecimal>, window: Int): BigDecimal? {
        if (closes.size < window || window <= 0) return null
        val slice = closes.takeLast(window)
        val sum = slice.fold(BigDecimal.ZERO) { acc, value -> acc.add(value, MoneyMath.CONTEXT) }
        return MoneyMath.div(sum, bd(window))
    }
}

object AlphaVantageParser {
    private val dailyCloseAlt = Regex("\"(\\d{4}-\\d{2}-\\d{2})\"\\s*:\\s*\\{[^}]*?\"4\\. close\"\\s*:\\s*\"([0-9.]+)\"")
    private val fxRate = Regex("\"5\\. Exchange Rate\"\\s*:\\s*\"([0-9.]+)\"")
    private val commodityItem = Regex("\"date\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"\\s*,\\s*\"value\"\\s*:\\s*\"([0-9.]+)\"")
    private val commodityMapRow = Regex("\"(\\d{4}-\\d{2}-\\d{2})\"\\s*:\\s*\"([0-9.]+)\"")

    fun dailyCloses(json: String): List<PriceBar> =
        dailyCloseAlt.findAll(json).map { match ->
            PriceBar(LocalDate.parse(match.groupValues[1]), BigDecimal(match.groupValues[2]))
        }.sortedBy { it.date }.toList()

    fun exchangeRate(json: String): BigDecimal? =
        fxRate.find(json)?.groupValues?.get(1)?.let { BigDecimal(it) }

    fun commoditySeries(json: String): List<PriceBar> {
        val fromItems = commodityItem.findAll(json).mapNotNull { match -> toBar(match.groupValues[1], match.groupValues[2]) }.toList()
        if (fromItems.isNotEmpty()) return fromItems.sortedBy { it.date }
        val dataBlock = json.substringAfter("\"data\"", missingDelimiterValue = json)
        return commodityMapRow.findAll(dataBlock).mapNotNull { match ->
            toBar(match.groupValues[1], match.groupValues[2])
        }.sortedBy { it.date }.toList()
    }

    private fun toBar(date: String, value: String): PriceBar? {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return PriceBar(parsed, BigDecimal(value))
    }

    fun analystRating(json: String): AnalystRating {
        fun count(label: String): Int =
            Regex("\"$label\"\\s*:\\s*\"?(\\d+)\"?").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val strongBuy = count("AnalystRatingStrongBuy")
        val buy = count("AnalystRatingBuy")
        val hold = count("AnalystRatingHold")
        val sell = count("AnalystRatingSell")
        val strongSell = count("AnalystRatingStrongSell")
        val total = strongBuy + buy + hold + sell + strongSell
        if (total == 0) return AnalystRating.NONE
        val score = (strongBuy * 5 + buy * 4 + hold * 3 + sell * 2 + strongSell * 1).toDouble() / total
        return when {
            score >= 4.5 -> AnalystRating.STRONG_BUY
            score >= 3.5 -> AnalystRating.BUY
            score >= 2.5 -> AnalystRating.HOLD
            score >= 1.5 -> AnalystRating.SELL
            else -> AnalystRating.STRONG_SELL
        }
    }
}

object StooqParser {
    fun dailyCloses(csv: String): List<PriceBar> =
        csv.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val cols = line.split(',')
                if (cols.size < 5) return@mapNotNull null
                val date = runCatching { LocalDate.parse(cols[0]) }.getOrNull() ?: return@mapNotNull null
                val close = runCatching { BigDecimal(cols[4]) }.getOrNull() ?: return@mapNotNull null
                PriceBar(date, close)
            }
            .sortedBy { it.date }
            .toList()
}

object FrankfurterParser {
    private val eur = Regex("\"EUR\"\\s*:\\s*([0-9.]+)")

    fun eurPerUsd(json: String): BigDecimal? =
        eur.find(json)?.groupValues?.get(1)?.let { BigDecimal(it) }
}

object CoinGeckoParser {
    /**
     * Parses `market_chart` prices: `{"prices":[[epochMs, price], ...]}`.
     */
    private val pair = Regex("\\[\\s*(\\d+)\\s*,\\s*([0-9.eE+-]+)\\s*\\]")

    fun dailyCloses(json: String): List<PriceBar> {
        val block = json.substringAfter("\"prices\"", missingDelimiterValue = json)
        return pair.findAll(block).map { match ->
            val epochDay = java.time.Instant.ofEpochMilli(match.groupValues[1].toLong())
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
            PriceBar(epochDay, BigDecimal(match.groupValues[2]))
        }.groupBy { it.date }
            .map { (date, bars) -> bars.last().copy(date = date) }
            .sortedBy { it.date }
    }
}
