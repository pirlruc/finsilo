package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.PieValueFormatter
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.YocReport
import com.pirlruc.finsilo.domain.model.RelativeToAverage
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatPercent
import com.pirlruc.finsilo.ui.formatSignedEur
import com.pirlruc.finsilo.ui.theme.chartColor
import com.pirlruc.finsilo.ui.theme.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onRangeSelected = viewModel::setRange,
        onLoadSample = viewModel::loadSample,
        onClear = viewModel::clearPortfolio,
        onSync = viewModel::syncMarketData,
        onSaveKey = viewModel::saveAlphaVantageKey,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRangeSelected: (HistoryRange) -> Unit,
    onLoadSample: () -> Unit,
    onClear: () -> Unit,
    onSync: () -> Unit,
    onSaveKey: (String) -> Unit,
) {
    var showKeyDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FinSilo") },
                actions = {
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(Icons.Outlined.Key, contentDescription = "Alpha Vantage key")
                    }
                    if (!state.empty) {
                        IconButton(onClick = onSync, enabled = !state.syncing) {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync quotes")
                        }
                    }
                    if (state.report != null) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Clear portfolio")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(state.error)
                state.empty -> EmptyState(onLoadSample)
                state.report != null -> DashboardContent(state.report, state.range, state.statusMessage, onRangeSelected)
            }
        }
    }
    if (showKeyDialog) {
        AlphaVantageKeyDialog(
            hasKey = state.hasAlphaVantageKey,
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                onSaveKey(key)
                showKeyDialog = false
            },
        )
    }
}

@Composable
private fun EmptyState(onLoadSample: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No holdings yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "This dashboard reads only the on-device encrypted ledger. Load a synthetic sample to review allocation, TWR, and YOC, or store an optional Alpha Vantage key and sync quotes (Frankfurter, Stooq, and CoinGecko work without a key).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLoadSample) { Text("Load sample portfolio") }
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Could not load dashboard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DashboardContent(
    report: DashboardReport,
    range: HistoryRange,
    statusMessage: String?,
    onRangeSelected: (HistoryRange) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (statusMessage != null) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        SummaryRow(report)
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
private fun SummaryRow(report: DashboardReport) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Portfolio",
            value = formatEur(report.allocation.totalValueEur),
            caption = "as of ${report.asOf}",
        )
        val pnl = report.allocation.unrealizedPnlEur
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Unrealized",
            value = formatSignedEur(pnl),
            caption = if (pnl.signum() >= 0) "Open gains" else "Open losses",
            valueColor = if (pnl.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "TWR",
            value = formatPercent(report.twr.twrPercent),
            caption = "${report.twr.subPeriods.size} sub-period(s)",
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    caption: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AllocationCard(slices: List<AllocationSlice>, total: java.math.BigDecimal) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Allocation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatEur(total)} · current EUR weight by asset class versus target. Drift beyond ±5% is highlighted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (slices.isEmpty()) {
                Text("Nothing to allocate.", modifier = Modifier.padding(top = 12.dp))
            } else {
                AllocationPie(slices, Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))
                slices.forEach { slice -> AllocationLegendRow(slice) }
            }
        }
    }
}

@Composable
private fun AllocationPie(slices: List<AllocationSlice>, modifier: Modifier = Modifier) {
    val modelProducer = remember { PieChartModelProducer() }
    LaunchedEffect(slices) {
        modelProducer.runTransaction {
            pieSeries { series(slices.map { it.weightPercent.toDouble() }) }
        }
    }
    val sliceStyles =
        slices.map { slice ->
            PieChart.Slice(
                fill = Fill(slice.assetType.chartColor()),
                label = PieChart.SliceLabel.Inside(
                    TextComponent(TextStyle(color = Color.White, fontWeight = FontWeight.Medium)),
                ),
            )
        }
    PieChartHost(
        chart =
            rememberPieChart(
                sliceProvider = PieChart.SliceProvider.series(sliceStyles),
                innerSize = PieSize.Inner.fixed(72.dp),
                spacing = 4.dp,
                valueFormatter = PieValueFormatter { _, value, _ -> "${"%.0f".format(value)}%" },
            ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

@Composable
private fun AllocationLegendRow(slice: AllocationSlice) {
    val drifted = slice.exceedsDriftBand
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(slice.assetType.chartColor()))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(slice.assetType.label(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val target = slice.targetPercent?.let { "target ${formatPercent(it)}" } ?: "no target"
            val drift = slice.driftPercent?.let { "drift ${formatPercent(it)}" }
            Text(
                listOfNotNull(target, drift).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (drifted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatEur(slice.valueEur), fontWeight = FontWeight.SemiBold)
            Text(formatPercent(slice.weightPercent), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryCard(
    points: List<NavPoint>,
    range: HistoryRange,
    onRangeSelected: (HistoryRange) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Portfolio history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Total net asset value in EUR, rebuilt each day from the ledger, closes, and FX history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryRange.entries.forEach { candidate ->
                    FilterChip(
                        selected = candidate == range,
                        onClick = { onRangeSelected(candidate) },
                        label = { Text(candidate.chipLabel()) },
                    )
                }
            }
            if (points.size < 2) {
                Text("Need at least two dates of history.", modifier = Modifier.padding(top = 8.dp))
            } else {
                HistoryLineChart(points, Modifier.fillMaxWidth().height(240.dp))
            }
        }
    }
}

@Composable
private fun HistoryLineChart(points: List<NavPoint>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel {
                series(
                    points.map { it.date.toEpochDay() },
                    points.map { it.valueEur.toDouble() },
                )
            }
        }
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM", Locale.Builder().setLanguage("pt").setRegion("PT").build())
    }
    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis =
                    VerticalAxis.rememberStart(
                        valueFormatter = { _, value, _ -> formatEur(value) },
                    ),
                bottomAxis =
                    HorizontalAxis.rememberBottom(
                        valueFormatter = { _, value, _ ->
                            LocalDate.ofEpochDay(value.toLong()).format(dateFormatter)
                        },
                    ),
            ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

@Composable
private fun SignalsCard(signals: List<MarketSignal>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ratings and moving averages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Latest on-device close versus SMA 50/200. Crosses use consecutive daily SMA observations (Phase 5 rule), shown here instead of only as a notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            signals.forEach { signal -> SignalRow(signal) }
        }
    }
}

@Composable
private fun SignalRow(signal: MarketSignal) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(signal.asset.symbol, fontWeight = FontWeight.SemiBold)
            Text(signal.rating.displayName, color = MaterialTheme.colorScheme.primary)
        }
        val parts = buildList {
            signal.vsSma50?.let { add("vs SMA50 ${it.label()}") }
            signal.vsSma200?.let { add("vs SMA200 ${it.label()}") }
            signal.cross?.let { add(it.label()) }
            if (signal.ratingChanged) {
                add("rating ${signal.previousRating?.displayName} → ${signal.rating.displayName}")
            }
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun HistoryRange.chipLabel(): String =
    when (this) {
        HistoryRange.ONE_MONTH -> "1M"
        HistoryRange.THREE_MONTHS -> "3M"
        HistoryRange.YTD -> "YTD"
        HistoryRange.ALL -> "All"
    }

private fun RelativeToAverage.label(): String =
    when (this) {
        RelativeToAverage.ABOVE -> "above"
        RelativeToAverage.BELOW -> "below"
    }

private fun TechnicalCross.label(): String =
    when (this) {
        TechnicalCross.GOLDEN -> "Golden cross"
        TechnicalCross.DEATH -> "Death cross"
    }

@Composable
private fun YocCard(yoc: List<YocReport>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Yield on cost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "TTM uses cash dividends in the last 12 months. Last×freq annualizes the most recent payment by the TTM count (1, 2, 4, or 12).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            yoc.forEach { row ->
                val ttm = row.ttmPercent?.let { formatPercent(it) } ?: "—"
                val last = row.lastTimesFrequencyPercent?.let { formatPercent(it) } ?: "—"
                val freq = row.paymentsPerYear?.toString() ?: "—"
                Text(
                    "${row.asset.symbol}  TTM $ttm  ·  last×$freq $last",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AlphaVantageKeyDialog(
    hasKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alpha Vantage key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasKey) {
                        "A key is stored on-device. Free tier is about 25 calls/day. FX still comes from Frankfurter, crypto from CoinGecko, and EU prices from Stooq without a key."
                    } else {
                        "Optional. Free tier is about 25 calls/day. FX still comes from Frankfurter, crypto from CoinGecko, and EU prices from Stooq without a key."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("API key") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (hasKey) {
                    TextButton(onClick = { onSave("") }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
