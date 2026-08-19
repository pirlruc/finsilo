package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.math.BigDecimal
import java.time.LocalDate

/** Outcome of recording a statement close for a locally valued instrument. */
sealed class ManualQuoteResult {
    /** Bar to persist; not a synthetic [com.pirlruc.finsilo.domain.model.Transaction]. */
    data class Accepted(val row: DailyMarketData) : ManualQuoteResult()

    /** User-visible reason the close was refused. */
    data class Rejected(val reason: String) : ManualQuoteResult()
}

/**
 * Stores a dated native close for unlisted PPR/CT/deposits. Listed tickers stay GET-only.
 */
class RecordManualQuoteUseCase {
    operator fun invoke(snapshot: PortfolioSnapshot, assetId: String, date: LocalDate, nativeClose: BigDecimal): ManualQuoteResult {
        val asset = snapshot.assets.firstOrNull { it.id == assetId }
            ?: return ManualQuoteResult.Rejected("Unknown instrument.")
        if (!asset.locallyValued) {
            return ManualQuoteResult.Rejected("Listed instruments stay on GET-only quotes.")
        }
        if (nativeClose.signum() <= 0) {
            return ManualQuoteResult.Rejected("Statement close must be a positive native price.")
        }
        return ManualQuoteResult.Accepted(
            DailyMarketData(
                assetId = assetId,
                date = date,
                closingPriceNative = nativeClose,
                analystRating = AnalystRating.NONE,
            ),
        )
    }
}
