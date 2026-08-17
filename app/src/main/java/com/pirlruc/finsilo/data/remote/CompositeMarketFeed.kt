package com.pirlruc.finsilo.data.remote

import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.CoinGeckoParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.ListedQuoteRouting
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.StooqParser
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PriceBar
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * Free-tier quotes only. Routing:
 * - FX: Frankfurter (ECB, no key), Alpha Vantage if a key is stored
 * - Crypto: CoinGecko `market_chart` in USD (EUR via stored FX; instrument may be booked in EUR)
 * - EU listings (`.DE` / Stooq suffix): Stooq, then Alpha Vantage
 * - Commodities: Stooq (XAUUSD) then Alpha Vantage commodity series / XAU FX (USD, EUR via FX)
 * - US stocks / ratings: Alpha Vantage (key, ~25 calls/day on the free tier)
 */
class CompositeMarketFeed(private val http: HttpGetClient = HttpGetClient(), private val keys: DatabaseKeyStore) : MarketFeed {

    override suspend fun eurPerUsd(): BigDecimal {
        runCatching {
            val json = http.get("https://api.frankfurter.app/latest?from=USD&to=EUR")
            FrankfurterParser.eurPerUsd(json)
        }.getOrNull()?.let { return it }

        val key = keys.alphaVantageKey() ?: throw IllegalStateException("No FX rate (Frankfurter failed, no Alpha Vantage key)")
        val json = http.get(
            "https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=USD&to_currency=EUR&apikey=${enc(key)}",
        )
        AlphaVantageParser.ensureUsable(json)
        return AlphaVantageParser.exchangeRate(json)
            ?: throw IllegalStateException("Alpha Vantage FX quota or unexpected payload")
    }

    override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> {
        val start = if (from.isAfter(to)) to else from
        runCatching {
            val json = http.get("https://api.frankfurter.app/$start..$to?from=USD&to=EUR")
            FrankfurterParser.eurPerUsdSeries(json)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        return listOf(CurrencyRate(to, eurPerUsd()))
    }

    override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> {
        val errors = ArrayList<String>()
        typedHistory(asset, asOf, errors)?.let { return it }
        listedHistory(asset.feedSymbol, errors)?.let { return it }
        throw IllegalStateException(errors.joinToString("; ").ifBlank { "No history for ${asset.feedSymbol}" })
    }

    private suspend fun typedHistory(asset: Asset, asOf: LocalDate, errors: MutableList<String>): List<PriceBar>? = when (asset.assetType) {
        AssetType.CRYPTO -> firstNonEmpty(errors) { coinGecko(asset) }
        AssetType.COMMODITY ->
            firstNonEmpty(errors) { stooqCommodity(asset) }
                ?: firstNonEmpty(errors) { alphaVantageCommodity(asset, asOf) }
                ?: throw IllegalStateException(
                    errors.joinToString("; ").ifBlank { "No history for ${asset.feedSymbol}" },
                )
        else -> null
    }

    private suspend fun listedHistory(ticker: String, errors: MutableList<String>): List<PriceBar>? {
        if (ListedQuoteRouting.looksEuropean(ticker)) {
            firstNonEmpty(errors) { stooq(ticker) }?.let { return it }
        }
        firstNonEmpty(errors) { alphaVantageDaily(ticker) }?.let { return it }
        return firstNonEmpty(errors) { stooq(ticker) }
    }

    private suspend fun firstNonEmpty(errors: MutableList<String>, block: suspend () -> List<PriceBar>): List<PriceBar>? =
        runCatching { block() }
            .onFailure { errors += it.message.orEmpty() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    override suspend fun analystRating(asset: Asset): AnalystRating {
        if (asset.assetType != AssetType.STOCK &&
            asset.assetType != AssetType.ETF &&
            asset.assetType != AssetType.PPR
        ) {
            return AnalystRating.NONE
        }
        val key = keys.alphaVantageKey() ?: return AnalystRating.NONE
        val json = http.get(
            "https://www.alphavantage.co/query?function=OVERVIEW&symbol=${enc(
                ListedQuoteRouting.avSymbol(asset.feedSymbol),
            )}&apikey=${enc(key)}",
        )
        AlphaVantageParser.ensureUsable(json)
        return AlphaVantageParser.analystRating(json)
    }

    private suspend fun coinGecko(asset: Asset): List<PriceBar> {
        val id = CRYPTO_IDS[asset.feedSymbol.uppercase()] ?: asset.feedSymbol.lowercase()
        val json = http.get("https://api.coingecko.com/api/v3/coins/$id/market_chart?vs_currency=usd&days=200&interval=daily")
        return CoinGeckoParser.dailyCloses(json)
    }

    private suspend fun stooq(symbol: String): List<PriceBar> {
        val ticker = ListedQuoteRouting.stooqTicker(symbol)
        val csv = http.get("https://stooq.com/q/d/l/?s=$ticker&i=d")
        return StooqParser.dailyCloses(csv)
    }

    private suspend fun alphaVantageDaily(symbol: String): List<PriceBar> {
        val key = keys.alphaVantageKey() ?: throw IllegalStateException("Alpha Vantage key required for $symbol")
        val json = http.get(
            "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=${enc(
                ListedQuoteRouting.avSymbol(symbol),
            )}&outputsize=full&apikey=${enc(key)}",
        )
        val bars = AlphaVantageParser.dailyCloses(json)
        if (bars.isEmpty()) throw IllegalStateException("Alpha Vantage daily empty for $symbol")
        return bars
    }

    private suspend fun alphaVantageCommodity(asset: Asset, asOf: LocalDate): List<PriceBar> {
        val key = keys.alphaVantageKey() ?: throw IllegalStateException("Alpha Vantage key required for commodities")
        val symbol = asset.feedSymbol.uppercase()
        if (symbol == "XAU" || symbol == "GOLD" || symbol == "XAUUSD") {
            val json = http.get(
                "https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=XAU&to_currency=USD&apikey=${enc(key)}",
            )
            val rate = AlphaVantageParser.exchangeRate(json) ?: throw IllegalStateException("No XAU spot")
            return listOf(PriceBar(asOf, rate))
        }
        val function = COMMODITY_FUNCTIONS[symbol]
            ?: throw IllegalStateException("Unknown commodity $symbol")
        val json = http.get(
            "https://www.alphavantage.co/query?function=$function&interval=daily&apikey=${enc(key)}",
        )
        return AlphaVantageParser.commoditySeries(json)
    }

    private suspend fun stooqCommodity(asset: Asset): List<PriceBar> {
        val ticker = COMMODITY_STOOQ[asset.feedSymbol.uppercase()] ?: return emptyList()
        val csv = http.get("https://stooq.com/q/d/l/?s=$ticker&i=d")
        return StooqParser.dailyCloses(csv)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private val CRYPTO_IDS = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "SOL" to "solana",
        )
        private val COMMODITY_STOOQ = mapOf(
            "XAU" to "xauusd",
            "GOLD" to "xauusd",
            "XAUUSD" to "xauusd",
        )
        private val COMMODITY_FUNCTIONS = mapOf(
            "WTI" to "WTI",
            "CL" to "WTI",
            "BRENT" to "BRENT",
            "NG" to "NATURAL_GAS",
            "COPPER" to "COPPER",
            "HG" to "COPPER",
            "WHEAT" to "WHEAT",
            "CORN" to "CORN",
            "COFFEE" to "COFFEE",
        )
    }
}
