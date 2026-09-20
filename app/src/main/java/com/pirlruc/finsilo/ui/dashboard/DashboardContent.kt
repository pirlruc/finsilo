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
        SummaryColumn(report)
        CapitalCard(report.allocation)
        HoldingsCard(
            report.allocation.holdings,
            report.allocation.cashEur,
            thresholds,
            ratingPrefs,
            holdings.onSaveThreshold,
            holdings.onSaveRating,
            holdings.onSaveInstrument,
        )
        AllocationCard(report.allocation.slices, report.allocation.totalValueEur)
        HistoryCard(report.history, range, onRangeSelected)
        if (report.yoc.isNotEmpty()) {
            YocCard(report.yoc)
        }
        if (report.signals.isNotEmpty()) {
            SignalsCard(report.signals)
        }
        if (importState != null && importNav != null) {
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
