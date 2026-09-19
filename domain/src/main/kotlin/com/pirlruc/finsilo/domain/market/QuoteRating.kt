package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData

/**
 * Rating to persist on a daily bar. The newest bar takes the latest overview;
 * older bars keep a stored non-NONE rating so a sync does not wipe history.
 */
object QuoteRating {
    fun onBar(isLatest: Boolean, latest: AnalystRating, stored: DailyMarketData?): AnalystRating {
        if (isLatest) return latest
        val previous = stored?.analystRating
        return if (previous != null && previous != AnalystRating.NONE) previous else AnalystRating.NONE
    }
}
