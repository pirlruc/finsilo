package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold

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
    val transactionsByAsset: Map<String, Int> = emptyMap(),
)
