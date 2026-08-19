package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.PieValueFormatter
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatPercent
import com.pirlruc.finsilo.ui.theme.chartColor
import com.pirlruc.finsilo.ui.theme.label
import java.math.BigDecimal

@Composable
internal fun AllocationCard(slices: List<AllocationSlice>, total: BigDecimal) {
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
                AllocationBody(slices)
            }
        }
    }
}

@Composable
private fun AllocationBody(slices: List<AllocationSlice>) {
    val pieSlices = slices.filter { it.valueEur.signum() > 0 && it.weightPercent.signum() > 0 }
    if (pieSlices.isNotEmpty()) {
        AllocationPie(pieSlices, Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
    slices.forEach { slice -> AllocationLegendRow(slice) }
}

@Composable
internal fun AllocationPie(slices: List<AllocationSlice>, modifier: Modifier = Modifier) {
    val modelProducer = remember { PieChartModelProducer() }
    LaunchedEffect(slices) {
        modelProducer.runTransaction {
            pieSeries { series(slices.map { it.weightPercent.toDouble() }) }
        }
    }
    val sliceStyles =
        slices.map { slice ->
            PieChart.Slice(fill = Fill(slice.assetType.chartColor()))
        }
    PieChartHost(
        chart =
        rememberPieChart(
            sliceProvider = PieChart.SliceProvider.series(sliceStyles),
            innerSize = PieSize.Inner.fixed(72.dp),
            spacing = 4.dp,
            valueFormatter = PieValueFormatter { _, _, _ -> "" },
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

@Composable
internal fun AllocationLegendRow(slice: AllocationSlice) {
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
