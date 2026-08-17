package com.pirlruc.finsilo.domain.model

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

enum class Currency {
    EUR,
    USD,
}

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
}

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

enum class HistoryRange {
    ONE_MONTH,
    THREE_MONTHS,
    YTD,
    ALL,
}

enum class TechnicalCross {
    GOLDEN,
    DEATH,
}

enum class RelativeToAverage {
    ABOVE,
    BELOW,
}
