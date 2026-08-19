package com.pirlruc.finsilo.data.remote

import java.time.LocalDate
import okhttp3.HttpUrl

/** Structured HTTPS URLs for the GET-only market feeds. */
object MarketFeedUrls {
    fun frankfurterLatest(): String = https("api.frankfurter.app")
        .addPathSegment("latest")
        .addQueryParameter("from", "USD")
        .addQueryParameter("to", "EUR")
        .toUrl()

    fun frankfurterRange(from: LocalDate, to: LocalDate): String = https("api.frankfurter.app")
        .addPathSegment("$from..$to")
        .addQueryParameter("from", "USD")
        .addQueryParameter("to", "EUR")
        .toUrl()

    fun coinGeckoChart(id: String): String = https("api.coingecko.com")
        .addPathSegments("api/v3/coins")
        .addPathSegment(MarketHttpsPolicy.requireSafeToken(id, "coin id"))
        .addPathSegment("market_chart")
        .addQueryParameter("vs_currency", "usd")
        .addQueryParameter("days", "200")
        .addQueryParameter("interval", "daily")
        .toUrl()

    fun stooqDaily(ticker: String): String = https("stooq.com")
        .addPathSegments("q/d/l")
        .addQueryParameter("s", MarketHttpsPolicy.requireSafeToken(ticker, "ticker"))
        .addQueryParameter("i", "d")
        .toUrl()

    fun alphaVantage(vararg query: Pair<String, String>): String {
        val builder = https("www.alphavantage.co").addPathSegment("query")
        for ((key, value) in query) {
            builder.addQueryParameter(key, value)
        }
        return builder.toUrl()
    }

    private fun https(host: String): HttpUrl.Builder = HttpUrl.Builder().scheme("https").host(host)

    private fun HttpUrl.Builder.toUrl(): String = build().toString()
}
