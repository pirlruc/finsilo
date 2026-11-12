package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.Transaction

data class DashboardUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val error: String? = null,
    val range: HistoryRange = HistoryRange.THREE_MONTHS,
    val report: DashboardReport? = null,
    val syncing: Boolean = false,
    val statusMessage: String? = null,
    val hasAlphaVantageKey: Boolean = false,
    val confirmClear: Boolean = false,
    val clearLedger: Boolean = true,
    val clearWatchlist: Boolean = true,
    val clearTemplates: Boolean = true,
    val thresholds: Map<String, PriceAlertThreshold> = emptyMap(),
    val ratingPrefs: Map<String, RatingAlertPref> = emptyMap(),
    val transactions: List<Transaction> = emptyList(),
    val assets: List<Asset> = emptyList(),
)

/** Which home tab is showing the dashboard report. */
enum class HomeSection {
    OVERVIEW,
    HOLDINGS,
    INSIGHTS,
    ALL,
}
