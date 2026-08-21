package com.pirlruc.finsilo.domain.model

/** Holding versus watchlist target for a rating notification set. */
enum class RatingAlertScope {
    HOLDING,
    WATCHLIST,
}

/**
 * Which [AnalystRating] levels notify for one holding or watchlist row.
 * A stored row with an empty [levels] set means "notify none".
 * A missing row uses [defaultsFor].
 */
data class RatingAlertPref(val targetId: String, val scope: RatingAlertScope, val levels: Set<AnalystRating>) {
    val mask: Int
        get() = maskOf(levels)

    companion object {
        private val HOLDING_DEFAULT = setOf(AnalystRating.SELL, AnalystRating.STRONG_SELL)
        private val WATCHLIST_DEFAULT = setOf(AnalystRating.BUY, AnalystRating.STRONG_BUY)

        fun defaultsFor(scope: RatingAlertScope): Set<AnalystRating> = when (scope) {
            RatingAlertScope.HOLDING -> HOLDING_DEFAULT
            RatingAlertScope.WATCHLIST -> WATCHLIST_DEFAULT
        }

        fun effective(pref: RatingAlertPref?, scope: RatingAlertScope): Set<AnalystRating> = pref?.levels ?: defaultsFor(scope)

        fun fromMask(targetId: String, scope: RatingAlertScope, mask: Int): RatingAlertPref =
            RatingAlertPref(targetId, scope, levelsOf(mask))

        fun maskOf(levels: Set<AnalystRating>): Int = levels.fold(0) { acc, rating -> acc or bit(rating) }

        fun levelsOf(mask: Int): Set<AnalystRating> =
            AnalystRating.entries.filter { it != AnalystRating.NONE && mask and bit(it) != 0 }.toSet()

        private fun bit(rating: AnalystRating): Int = when (rating) {
            AnalystRating.NONE -> 0
            AnalystRating.STRONG_SELL -> 1
            AnalystRating.SELL -> 2
            AnalystRating.HOLD -> 4
            AnalystRating.BUY -> 8
            AnalystRating.STRONG_BUY -> 16
        }
    }
}
