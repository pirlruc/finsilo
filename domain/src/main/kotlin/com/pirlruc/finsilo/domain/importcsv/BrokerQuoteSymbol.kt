package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.market.ListedQuoteRouting

/** Map broker tickers and ISINs onto Stooq/AV [Asset.quoteSymbol] values. */
internal object BrokerQuoteSymbol {
    private val t212Exchange =
        mapOf(
            "US" to "US",
            "UK" to "L",
            "GY" to "DE",
            "DE" to "DE",
            "GE" to "DE",
            "NA" to "AS",
            "AS" to "AS",
            "FP" to "PA",
            "PA" to "PA",
            "SW" to "SW",
            "IM" to "MI",
            "MI" to "MI",
            "MC" to "MC",
            "LS" to "LS",
            "BB" to "BR",
        )

    private val venueSuffix =
        mapOf(
            "NDQ" to "US",
            "NSY" to "US",
            "NASDAQ" to "US",
            "NYSE" to "US",
            "XET" to "DE",
            "XETR" to "DE",
            "FRA" to "DE",
            "EPA" to "PA",
            "PAR" to "PA",
            "AMS" to "AS",
            "LSE" to "L",
            "LON" to "L",
            "MAD" to "MC",
            "MIL" to "MI",
            "SWX" to "SW",
            "ELI" to "LS",
            "LIS" to "LS",
            "BRU" to "BR",
        )

    private val isinCountry =
        mapOf(
            "US" to "US",
            "DE" to "DE",
            "NL" to "AS",
            "FR" to "PA",
            "GB" to "L",
            "ES" to "MC",
            "IT" to "MI",
            "CH" to "SW",
            "PT" to "LS",
            "BE" to "BR",
            "IE" to "DE",
        )

    fun fromTrading212(ticker: String): String {
        val trimmed = ticker.trim()
        if (trimmed.isEmpty()) return trimmed
        val parts = trimmed.split('_')
        if (parts.size < 2) return trimmed
        val root = parts.first()
        val exchange = parts.getOrNull(parts.lastIndex - 1)?.uppercase()
        val suffix = t212Exchange[exchange] ?: return root
        return "$root.$suffix"
    }

    fun fromVenue(symbol: String, venue: String, isin: String?): String {
        val root = symbol.substringBefore('.').ifBlank { isin.orEmpty() }
        if (root.isEmpty()) return symbol
        val fromVenue = venueSuffix[venue.trim().uppercase()]
        val fromIsin = isin?.take(2)?.uppercase()?.let { isinCountry[it] }
        val suffix = fromVenue ?: fromIsin ?: return root
        return "$root.$suffix"
    }

    fun fromIsin(ticker: String, isin: String?): String {
        val root = ticker.substringBefore('.').ifBlank { return ticker }
        if (ListedQuoteRouting.looksEuropean(ticker) || ticker.contains('.')) return ticker
        val suffix = isin?.take(2)?.uppercase()?.let { isinCountry[it] } ?: return ticker
        return "$root.$suffix"
    }
}
