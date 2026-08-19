package com.pirlruc.finsilo.domain.model

/** Investment type stored on an asset row and used for pie slices. */
enum class AssetType {
    STOCK,
    ETF,
    CRYPTO,
    DEPOSIT,
    PPR,
    CT,
    COMMODITY,
    CASH,
    ;

    /** Bank deposits and Portuguese CTs have no market feed; NAV is principal + interest. */
    val isLocallyValued: Boolean
        get() = this == DEPOSIT || this == CT

    val allowsInterest: Boolean
        get() = this == DEPOSIT || this == CT || this == PPR

    val allowsDividend: Boolean
        get() = this == STOCK || this == ETF || this == PPR
}

/** Quote currency. Valuation stores FX as EUR per 1 USD. */
enum class Currency {
    EUR,
    USD,
}

/** Ledger row kind. Same-day replay uses [ledgerRank]. */
enum class TransactionType {
    BUY,
    SELL,
    DEPOSIT_CASH,
    WITHDRAWAL,
    DIVIDEND,
    INTEREST,
    ;

    /**
     * Same-day replay order so a deposit funds a buy regardless of UUID ordering.
     * Deposits and income first, then sells, then buys, then withdrawals.
     */
    val ledgerRank: Int
        get() =
            when (this) {
                DEPOSIT_CASH -> 0
                DIVIDEND -> 1
                INTEREST -> 2
                SELL -> 3
                BUY -> 4
                WITHDRAWAL -> 5
            }

    /**
     * Same-day CSV apply order. Sells run before buys so same-day purchases are
     * not sellable. Income runs after buys so a first-time purchase can match a
     * dividend row (the import walk needs the instrument in the snapshot).
     */
    val csvImportRank: Int
        get() =
            when (this) {
                DEPOSIT_CASH -> 0
                SELL -> 1
                BUY -> 2
                DIVIDEND -> 3
                INTEREST -> 4
                WITHDRAWAL -> 5
            }
}

/** Alpha Vantage OVERVIEW consensus mapped to a five-level scale. */
enum class AnalystRating(val code: Int) {
    NONE(0),
    STRONG_SELL(1),
    SELL(2),
    HOLD(3),
    BUY(4),
    STRONG_BUY(5),
    ;

    val displayName: String
        get() =
            when (this) {
                NONE -> "None"
                STRONG_SELL -> "Strong Sell"
                SELL -> "Sell"
                HOLD -> "Hold"
                BUY -> "Buy"
                STRONG_BUY -> "Strong Buy"
            }

    companion object {
        fun fromCode(code: Int): AnalystRating = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** Dashboard NAV chart window. */
enum class HistoryRange {
    ONE_MONTH,
    THREE_MONTHS,
    YTD,
    ALL,
}

/** SMA 50/200 cross on consecutive stored bars. */
enum class TechnicalCross {
    GOLDEN,
    DEATH,
}

/** Price versus a stored moving average. */
enum class RelativeToAverage {
    ABOVE,
    BELOW,
}
