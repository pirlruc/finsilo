package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory

/** Extra dashboard footnotes that are not valuation failures. */
object DashboardCopy {
    const val CSV_TWR: String =
        "Some buys share a date with a cash deposit, so TWR treats those buys as internal and may not split."

    const val SAMPLE_CROSS: String =
        "Sample prices are synthetic. AAPL may show a demo golden cross that live sync would not invent."

    /** Warnings for same-day cash top-ups that fund buys, and the synthetic sample book. */
    fun extras(snapshot: PortfolioSnapshot): List<String> {
        val extra = ArrayList<String>(2)
        if (csvFundedBuys(snapshot)) extra += CSV_TWR
        if (snapshot.assets.any { it.id == SamplePortfolioFactory.APPLE_ID }) extra += SAMPLE_CROSS
        return extra
    }

    /** True when a BUY shares a calendar date with a cash deposit (manual or CSV funding). */
    fun csvFundedBuys(snapshot: PortfolioSnapshot): Boolean {
        val depositDates =
            snapshot.transactions.filter { it.type == TransactionType.DEPOSIT_CASH }.map { it.date }.toHashSet()
        if (depositDates.isEmpty()) return false
        return snapshot.transactions.any { it.type == TransactionType.BUY && it.date in depositDates }
    }
}
