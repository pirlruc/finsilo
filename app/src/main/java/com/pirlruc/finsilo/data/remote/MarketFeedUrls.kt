package com.pirlruc.finsilo.data.remote

import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    fun coinGeckoChart(id: String, days: Int = 200): String = https("api.coingecko.com")
        .addPathSegments("api/v3/coins")
        .addPathSegment(MarketHttpsPolicy.requireSafeToken(id, "coin id"))
        .addPathSegment("market_chart")
        .addQueryParameter("vs_currency", "usd")
        .addQueryParameter("days", days.toString())
        .addQueryParameter("interval", "daily")
        .toUrl()

    fun stooqDaily(ticker: String, from: LocalDate? = null): String {
        val builder = https("stooq.com")
            .addPathSegments("q/d/l")
            .addQueryParameter("s", MarketHttpsPolicy.requireSafeToken(ticker, "ticker"))
            .addQueryParameter("i", "d")
        if (from != null) {
            builder.addQueryParameter("d1", from.format(DateTimeFormatter.BASIC_ISO_DATE))
        }
        return builder.toUrl()
    }

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
