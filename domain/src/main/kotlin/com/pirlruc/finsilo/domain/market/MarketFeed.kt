package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** GET-only quote source used by [com.pirlruc.finsilo.domain.usecase.SyncMarketDataUseCase]. */
interface MarketFeed {
    suspend fun eurPerUsd(): BigDecimal

    suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate>

    suspend fun dailyHistory(asset: Asset, asOf: LocalDate = LocalDate.now()): List<PriceBar>

    /**
     * Compact existence/seed fetch. Default is [dailyHistory]; production feeds
     * should request a short window (Alpha Vantage `compact`) instead of full.
     */
    suspend fun probeHistory(asset: Asset, asOf: LocalDate = LocalDate.now()): List<PriceBar> = dailyHistory(asset, asOf)

    suspend fun analystRating(asset: Asset): AnalystRating
}

/** Simple moving averages computed from stored closes (no extra API calls). */
object MovingAverages {
    fun sma(closes: List<BigDecimal>, window: Int): BigDecimal? {
        if (closes.size < window || window <= 0) return null
        val slice = closes.takeLast(window)
        val sum = slice.fold(BigDecimal.ZERO) { acc, value -> acc.add(value, MoneyMath.CONTEXT) }
        return MoneyMath.div(sum, bd(window))
    }
}

/** Regex parser for Alpha Vantage JSON (daily, FX, OVERVIEW, commodities). */
object AlphaVantageParser {
    private val dailyCloseAlt = Regex("\"(\\d{4}-\\d{2}-\\d{2})\"\\s*:\\s*\\{[^}]*?\"4\\. close\"\\s*:\\s*\"([0-9.]+)\"")
    private val fxRate = Regex("\"5\\. Exchange Rate\"\\s*:\\s*\"([0-9.]+)\"")
    private val commodityItem = Regex("\"date\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"\\s*,\\s*\"value\"\\s*:\\s*\"([0-9.]+)\"")
    private val commodityMapRow = Regex("\"(\\d{4}-\\d{2}-\\d{2})\"\\s*:\\s*\"([0-9.]+)\"")
    private val quotedField = { key: String -> Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"") }

    fun ensureUsable(json: String) {
        extract(json, "Error Message")?.let { error("Alpha Vantage: $it") }
        extract(json, "Note")?.let { error("Alpha Vantage limit: $it") }
        extract(json, "Information")?.let { error("Alpha Vantage: $it") }
    }

    private fun extract(json: String, key: String): String? = quotedField(key).find(json)?.groupValues?.get(1)

    fun dailyCloses(json: String): List<PriceBar> {
        ensureUsable(json)
        return dailyCloseAlt
            .findAll(json)
            .map { match ->
                PriceBar(LocalDate.parse(match.groupValues[1]), BigDecimal(match.groupValues[2]))
            }.sortedBy { it.date }
            .toList()
    }

    fun exchangeRate(json: String): BigDecimal? {
        ensureUsable(json)
        val match = fxRate.find(json) ?: return null
        return BigDecimal(match.groupValues[1])
    }

    fun commoditySeries(json: String): List<PriceBar> {
        ensureUsable(json)
        val fromItems = commodityItem.findAll(json).mapNotNull { match -> toBar(match.groupValues[1], match.groupValues[2]) }.toList()
        if (fromItems.isNotEmpty()) return fromItems.sortedBy { it.date }
        val dataBlock = json.substringAfter("\"data\"", missingDelimiterValue = json)
        return commodityMapRow
            .findAll(dataBlock)
            .mapNotNull { match ->
                toBar(match.groupValues[1], match.groupValues[2])
            }.sortedBy { it.date }
            .toList()
    }

    private fun toBar(date: String, value: String): PriceBar? {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return PriceBar(parsed, BigDecimal(value))
    }

    fun analystRating(json: String): AnalystRating {
        ensureUsable(json)

        fun count(label: String): Int {
            val match = Regex("\"$label\"\\s*:\\s*\"?([^\"\\s}]+)\"?").find(json) ?: return 0
            return match.groupValues[1].toIntOrNull() ?: 0
        }
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

/** Parser for Stooq daily CSV. */
object StooqParser {
    fun dailyCloses(csv: String): List<PriceBar> = csv
        .lineSequence()
        .drop(1)
        .mapNotNull { line ->
            val cols = line.split(',')
            if (cols.size < 5) return@mapNotNull null
            val date = runCatching { LocalDate.parse(cols[0]) }.getOrNull() ?: return@mapNotNull null
            val close = runCatching { BigDecimal(cols[4]) }.getOrNull() ?: return@mapNotNull null
            PriceBar(date, close)
        }.sortedBy { it.date }
        .toList()
}

/** Typed parser for Frankfurter JSON. Reads rate objects, not a currency regex. */
object FrankfurterParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun eurPerUsd(body: String): BigDecimal? = numberFor(body, "EUR")

    fun eurPerUsdSeries(body: String): List<CurrencyRate> = crossSeries(body).map { (date, rates) ->
        CurrencyRate(date, rates[Currency.USD] ?: rates.values.first(), rates.filterKeys { it != Currency.USD })
    }

    /** Latest EUR-per-unit map. Keys are the quoted currencies present in the document. */
    fun latestCrosses(body: String): Map<Currency, BigDecimal> {
        val root = root(body) ?: return emptyMap()
        val rates = root["rates"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: root
        return rates.entries.mapNotNull { (code, value) ->
            val currency = Currency.fromCode(code) ?: return@mapNotNull null
            number(value)?.let { currency to it }
        }.toMap()
    }

    private fun crossSeries(body: String): List<Pair<LocalDate, Map<Currency, BigDecimal>>> {
        val root = root(body) ?: return emptyList()
        val table = root["rates"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: root
        return table.entries.mapNotNull { (key, value) ->
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
            val obj = runCatching { value.jsonObject }.getOrNull() ?: return@mapNotNull null
            val rates = obj.entries.mapNotNull { (code, amount) ->
                val currency = Currency.fromCode(code) ?: return@mapNotNull null
                number(amount)?.let { currency to it }
            }.toMap()
            if (rates.isEmpty()) null else date to rates
        }.sortedBy { it.first }
    }

    private fun numberFor(body: String, code: String): BigDecimal? {
        val root = root(body) ?: return null
        number(root[code])?.let { return it }
        val rates = root["rates"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        return number(rates[code])
    }

    private fun root(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun number(value: kotlinx.serialization.json.JsonElement?): BigDecimal? {
        val primitive = value as? JsonPrimitive ?: return null
        val text = primitive.contentOrNull ?: return null
        return text.toBigDecimalOrNull()
    }
}

/** Typed parser for CoinGecko `market_chart`. Only [prices] is read. */
object CoinGeckoParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Chart(val prices: List<List<Double>> = emptyList())

    fun dailyCloses(body: String): List<PriceBar> {
        val chart = runCatching { json.decodeFromString<Chart>(body) }.getOrNull() ?: return emptyList()
        return chart.prices.mapNotNull { pair ->
            if (pair.size < 2) return@mapNotNull null
            val epochDay = java.time.Instant.ofEpochMilli(pair[0].toLong())
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
            PriceBar(epochDay, BigDecimal.valueOf(pair[1]))
        }.groupBy { it.date }
            .map { (date, bars) -> bars.last().copy(date = date) }
            .sortedBy { it.date }
    }
}
