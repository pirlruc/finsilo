package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.ui.formatEur
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryCard(points: List<NavPoint>, range: HistoryRange, onRangeSelected: (HistoryRange) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Portfolio history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Total net asset value in EUR, rebuilt each day from the ledger, closes, and FX history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
internal fun HistoryLineChart(points: List<NavPoint>, modifier: Modifier = Modifier) {
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

internal fun HistoryRange.chipLabel(): String = when (this) {
    HistoryRange.ONE_MONTH -> "1M"
    HistoryRange.THREE_MONTHS -> "3M"
    HistoryRange.YTD -> "YTD"
    HistoryRange.ALL -> "All"
}
