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
            "DK" to "CO",
        )

    fun fromTrading212(ticker: String, isin: String? = null): String {
        val trimmed = ticker.trim()
        if (trimmed.isEmpty()) return trimmed
        val listed = listedTicker(trimmed)
        if (listed != null) return listed
        val suffix = bareListingSuffix(isin) ?: return trimmed
        return "$trimmed.$suffix"
    }

    private fun listedTicker(trimmed: String): String? {
        val parts = trimmed.split('_')
        if (parts.size < 2) return trimmed.takeIf { '.' in it }
        val root = parts.first()
        val exchange = parts.getOrNull(parts.lastIndex - 1)?.uppercase()
        val suffix = t212Exchange[exchange] ?: return root
        return "$root.$suffix"
    }

    /** IE and DK listings arrive without an exchange suffix on Trading 212. */
    private fun bareListingSuffix(isin: String?): String? = when (isin?.take(2)?.uppercase()) {
        "IE" -> "DE"
        "DK" -> "CO"
        else -> null
    }

    /** DEGIRO exports a product name and ISIN, not a listed ticker. */
    fun fromDegiro(product: String, isin: String?): String = isin?.ifBlank { null } ?: product

    fun fromIsin(ticker: String, isin: String?): String {
        val root = ticker.substringBefore('.').ifBlank { return ticker }
        if (keepListedTicker(ticker)) return ticker
        val suffix = isin?.take(2)?.uppercase()?.let { isinCountry[it] } ?: return ticker
        return "$root.$suffix"
    }

    private fun keepListedTicker(ticker: String): Boolean {
        if (ListedQuoteRouting.looksEuropean(ticker)) return true
        return ticker.contains('.')
    }
}
