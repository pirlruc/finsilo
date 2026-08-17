package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency

/**
 * Currency of GET-only market closes, which can differ from the instrument's
 * booking currency.
 *
 * CoinGecko, Alpha Vantage US/commodity series, and Stooq XAU stay USD. Those
 * quotes convert to EUR with stored EUR-per-USD (Frankfurter, then Alpha
 * Vantage). European listings stay EUR. Booking a crypto or commodity in EUR
 * does not change the feed currency.
 */
object QuoteCurrency {
    /** Quote currency of the live feed used for [asset], not the ledger booking currency. */
    fun of(asset: Asset): Currency {
        if (asset.locallyValued || asset.assetType == AssetType.CASH) return asset.baseCurrency
        if (usdQuotedType(asset.assetType)) return Currency.USD
        return if (ListedQuoteRouting.looksEuropean(asset.feedSymbol)) Currency.EUR else Currency.USD
    }

    /** True when mark-to-market needs an EUR-per-USD row even if the instrument is booked in EUR. */
    fun needsUsdFx(asset: Asset): Boolean = !asset.locallyValued && asset.assetType != AssetType.CASH && of(asset) == Currency.USD

    private fun usdQuotedType(type: AssetType): Boolean = type == AssetType.CRYPTO || type == AssetType.COMMODITY
}
