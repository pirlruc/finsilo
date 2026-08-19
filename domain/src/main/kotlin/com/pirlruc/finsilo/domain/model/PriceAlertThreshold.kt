package com.pirlruc.finsilo.domain.model

import java.math.BigDecimal

/**
 * Per-holding alert levels evaluated on stored daily bars (not a new market feed).
 *
 * [eurLevel] fires when consecutive EUR quotes cross that level.
 * [percentMove] fires when the absolute day-over-day move is at least that percent.
 */
data class PriceAlertThreshold(val assetId: String, val eurLevel: BigDecimal? = null, val percentMove: BigDecimal? = null) {
    val isEmpty: Boolean
        get() = eurLevel == null && percentMove == null
}
