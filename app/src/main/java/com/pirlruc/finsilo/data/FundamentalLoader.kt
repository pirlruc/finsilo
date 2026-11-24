package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.FundamentalCacheEntity
import com.pirlruc.finsilo.data.local.PortfolioDao
import com.pirlruc.finsilo.data.remote.OpenBbFundamentalsClient
import com.pirlruc.finsilo.domain.fundamentals.FundamentalCodec
import com.pirlruc.finsilo.domain.fundamentals.FundamentalView
import com.pirlruc.finsilo.domain.fundamentals.ValuationNote
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import java.math.BigDecimal
import java.time.LocalDate

/** Seven-day Room cache in front of Alpha Vantage or an optional OpenBB source. */
class FundamentalCache(private val dao: PortfolioDao) {
    suspend fun read(assetId: String, today: LocalDate): FundamentalView? {
        val row = dao.fundamental(assetId) ?: return null
        if (today.toEpochDay() - row.fetchedEpochDay >= CACHE_DAYS) return null
        return FundamentalCodec.decode(row.payload)
    }

    suspend fun write(assetId: String, view: FundamentalView, today: LocalDate) {
        dao.upsertFundamental(FundamentalCacheEntity(assetId, FundamentalCodec.encode(view), today.toEpochDay()))
    }

    companion object {
        const val CACHE_DAYS: Long = 7
    }
}

class FundamentalLoader(
    private val cache: FundamentalCache,
    private val feed: MarketFeed,
    private val openBb: OpenBbFundamentalsClient,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun load(asset: Asset, price: BigDecimal?): FundamentalView {
        if (asset.assetType != AssetType.STOCK) return FundamentalView.notApplicable()
        cache.read(asset.id, today())?.let { return it }
        val fresh = if (openBb.enabled()) openBb.load(asset.feedSymbol, price, today()) else feed.fundamentals(asset, price, today())
        if (fresh.note == ValuationNote.RATIOS || fresh.note == ValuationNote.NOT_APPLICABLE) {
            cache.write(asset.id, fresh, today())
        }
        return fresh
    }
}
