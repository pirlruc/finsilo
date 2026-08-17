package com.pirlruc.finsilo.domain.market

/**
 * Maps listed tickers to Stooq-first (European) vs Alpha Vantage-first (US) routing.
 * Yahoo-style suffixes such as `.L` and `.SW` are rewritten to Stooq's `.uk` / `.sw`.
 */
object ListedQuoteRouting {
    private val STOOQ_SUFFIX =
        mapOf(
            "DE" to "de",
            "PA" to "pa",
            "AS" to "as",
            "MI" to "mi",
            "MC" to "mc",
            "BR" to "br",
            "LS" to "ls",
            "L" to "uk",
            "LON" to "uk",
            "UK" to "uk",
            "SW" to "sw",
            "VX" to "sw",
            "HE" to "he",
            "ST" to "st",
            "OL" to "ol",
            "CO" to "co",
            "IR" to "ir",
            "VI" to "vi",
            "AT" to "vi",
        )

    /** True when Stooq should be tried before Alpha Vantage. */
    fun looksEuropean(symbol: String): Boolean {
        val upper = symbol.trim().uppercase()
        if (upper.startsWith("PTY")) return true
        val suffix = suffixOf(upper) ?: return false
        return suffix in STOOQ_SUFFIX
    }

    /** Stooq daily CSV ticker (`vwce.de`, `shel.uk`, `aapl.us`). */
    fun stooqTicker(symbol: String): String {
        val trimmed = symbol.trim()
        val upper = trimmed.uppercase()
        val suffix = suffixOf(upper)
        val root = if (suffix == null) trimmed.lowercase() else trimmed.substringBeforeLast('.').lowercase()
        if (suffix == null) {
            return if (upper.startsWith("PTY")) root else "$root.us"
        }
        val mapped = STOOQ_SUFFIX[suffix] ?: suffix.lowercase()
        return "$root.$mapped"
    }

    /** Alpha Vantage equity symbol (exchange suffix stripped). */
    fun avSymbol(symbol: String): String = symbol.trim().substringBefore('.')

    private fun suffixOf(upper: String): String? {
        val dot = upper.lastIndexOf('.')
        if (dot < 0 || dot == upper.lastIndex) return null
        return upper.substring(dot + 1)
    }
}
