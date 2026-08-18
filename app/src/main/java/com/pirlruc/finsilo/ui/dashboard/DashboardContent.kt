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
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatPercent
import com.pirlruc.finsilo.ui.formatSignedEur

@Composable
internal fun DashboardContent(
    report: DashboardReport,
    range: HistoryRange,
    statusMessage: String?,
    thresholds: Map<String, PriceAlertThreshold>,
    onRangeSelected: (HistoryRange) -> Unit,
    onSaveThreshold: (PriceAlertThreshold) -> Unit,
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
        HoldingsCard(report.allocation.holdings, report.allocation.cashEur, thresholds, onSaveThreshold)
        AllocationCard(report.allocation.slices, report.allocation.totalValueEur)
        HistoryCard(report.history.points, range, onRangeSelected)
        if (report.yoc.isNotEmpty()) {
            YocCard(report.yoc)
        }
        if (report.signals.isNotEmpty()) {
            SignalsCard(report.signals)
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
                valueColor = if (pnl.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                description = "Unrealized ${formatSignedEur(pnl)}",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "TWR",
                value = formatPercent(report.twr.twrPercent),
                caption = "${report.twr.subPeriods.size} sub-period(s)",
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
