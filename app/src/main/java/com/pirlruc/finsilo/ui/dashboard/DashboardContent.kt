package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.TwrReport
import com.pirlruc.finsilo.domain.model.TwrSplit
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatPercent
import com.pirlruc.finsilo.ui.formatSignedEur
import com.pirlruc.finsilo.ui.importcsv.BrokerImportCard
import com.pirlruc.finsilo.ui.importcsv.BrokerImportUiState

@Composable
internal fun DashboardContent(
    report: DashboardReport,
    range: HistoryRange,
    statusMessage: String?,
    thresholds: Map<String, PriceAlertThreshold>,
    ratingPrefs: Map<String, RatingAlertPref>,
    onRangeSelected: (HistoryRange) -> Unit,
    holdings: HoldingActions,
    importState: BrokerImportUiState? = null,
    importNav: ImportNavActions? = null,
    section: HomeSection = HomeSection.ALL,
    transactions: List<com.pirlruc.finsilo.domain.model.Transaction> = emptyList(),
    assets: List<com.pirlruc.finsilo.domain.model.Asset> = emptyList(),
    performance: PerformanceSummary? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (statusMessage != null) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        report.warnings.forEach { warning ->
            Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (shows(section, HomeSection.OVERVIEW)) {
            OverviewColumn(report, range, onRangeSelected, performance)
        }
        if (shows(section, HomeSection.HOLDINGS)) {
            HoldingsColumn(report, thresholds, ratingPrefs, holdings, transactions, assets)
        }
        if (shows(section, HomeSection.INSIGHTS)) {
            InsightsColumn(report, transactions, assets, performance)
        }
        if (shows(section, HomeSection.OVERVIEW) && importState != null && importNav != null) {
            BrokerImportCard(
                state = importState,
                onImportCsvs = importNav.onImportCsvs,
                onPickerBusy = importNav.onPickerBusy,
                onQuoteSymbol = importNav.onQuoteSymbol,
                onConfirmReview = importNav.onConfirmReview,
                onCancelReview = importNav.onCancelReview,
                onPickerError = importNav.onPickerError,
            )
        }
    }
}

@Composable
internal fun SummaryColumn(report: DashboardReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            modifier = Modifier.fillMaxWidth(),
            label = "Portfolio",
            value = formatEur(report.allocation.totalValueEur),
            caption = "as of ${report.asOf}",
            description = "Total net asset value ${formatEur(report.allocation.totalValueEur)}",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val pnl = report.allocation.unrealizedPnlEur
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Unrealized",
                value = formatSignedEur(pnl),
                caption = if (pnl.signum() >= 0) "Open gains" else "Open losses",
                valueColor = signedAmountColor(pnl),
                description = "Unrealized ${formatSignedEur(pnl)}",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Net invested",
                value = formatEur(report.allocation.contributedEur),
                caption = "Capital in",
                description = "Net invested ${formatEur(report.allocation.contributedEur)}",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Return",
                value = formatSignedEur(report.allocation.totalGainEur),
                caption = "Gain or loss",
                valueColor = signedAmountColor(report.allocation.totalGainEur),
                description = "Return ${formatSignedEur(report.allocation.totalGainEur)}",
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "TWR",
                value = formatPercent(report.twr.twrPercent),
                caption = twrCaption(report.twr),
                valueColor = signedAmountColor(report.twr.twrPercent),
                description = "Time-weighted return ${formatPercent(report.twr.twrPercent)}",
            )
        }
    }
}

@Composable
internal fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    caption: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    description: String = "$label $value",
) {
    Card(
        modifier.semantics { contentDescription = description },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shows(section: HomeSection, part: HomeSection): Boolean = section == part || section == HomeSection.ALL

@Composable
private fun OverviewColumn(
    report: DashboardReport,
    range: HistoryRange,
    onRangeSelected: (HistoryRange) -> Unit,
    performance: PerformanceSummary?,
) {
    SummaryColumn(report)
    performance?.let { PerformanceLine(it.xirr, it.benchmark) }
    CapitalCard(report.allocation)
    HistoryCard(report.history, range, onRangeSelected)
}

@Composable
private fun HoldingsColumn(
    report: DashboardReport,
    thresholds: Map<String, PriceAlertThreshold>,
    ratingPrefs: Map<String, RatingAlertPref>,
    holdings: HoldingActions,
    transactions: List<com.pirlruc.finsilo.domain.model.Transaction>,
    assets: List<com.pirlruc.finsilo.domain.model.Asset>,
) {
    HoldingsBrowser(
        holdings = report.allocation.holdings,
        cashEur = report.allocation.cashEur,
        thresholds = thresholds,
        ratingPrefs = ratingPrefs,
        actions = holdings,
        transactions = transactions,
        assets = assets,
    )
}

@Composable
private fun InsightsColumn(
    report: DashboardReport,
    transactions: List<com.pirlruc.finsilo.domain.model.Transaction>,
    assets: List<com.pirlruc.finsilo.domain.model.Asset>,
    performance: PerformanceSummary?,
) {
    AllocationCard(report.allocation.slices, report.allocation.totalValueEur)
    RebalanceCard(report.allocation.slices, report.allocation.totalValueEur)
    if (report.yoc.isNotEmpty()) YocCard(report.yoc)
    if (report.signals.isNotEmpty()) SignalsCard(report.signals)
    MaisValiasPreview(transactions, assets, report.asOf.year)
    performance?.let { PerformanceLine(it.dividends, it.taxNote) }
}

@Composable
internal fun PerformanceLine(primary: String, secondary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(primary, style = MaterialTheme.typography.bodyMedium)
        Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun twrCaption(report: TwrReport): String {
    val periods = report.subPeriods
    if (periods.isEmpty()) return "0 sub-period(s)"
    val span = "${periods.first().from}–${periods.last().to}"
    val splits = periods.mapNotNull { it.split }.distinct()
    val splitLabel = splits.takeIf { it.isNotEmpty() }?.joinToString { it.caption() }
    return listOfNotNull("${periods.size} sub-period(s)", span, splitLabel).joinToString(" · ")
}

private fun TwrSplit.caption(): String = when (this) {
    TwrSplit.EXTERNAL_BUY -> "external buy"
    TwrSplit.WITHDRAWAL -> "withdrawal"
}
