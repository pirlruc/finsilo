package com.pirlruc.finsilo.domain.model

/**
 * A followed symbol that must never be mixed into [PortfolioSnapshot.transactions].
 * Quotes live in [quotes] only; allocation, TWR, and YOC ignore this type.
 */
data class WatchlistItem(
    val id: String,
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val baseCurrency: Currency,
    val quoteSymbol: String? = null,
) {
    /** Listed ticker used by GET-only routing. */
    val feedSymbol: String
        get() = quoteSymbol?.takeIf { it.isNotBlank() } ?: symbol

    /** Feed [Asset] used only to call [com.pirlruc.finsilo.domain.market.MarketFeed]. */
    fun asFeedAsset(): Asset = Asset(
        id = id,
        symbol = symbol,
        name = name,
        assetType = assetType,
        baseCurrency = baseCurrency,
        quoteSymbol = quoteSymbol,
    )
}

/** Isolated watchlist store. Never merge [quotes] into [PortfolioSnapshot.marketData]. */
data class WatchlistSnapshot(val items: List<WatchlistItem> = emptyList(), val quotes: List<DailyMarketData> = emptyList())
