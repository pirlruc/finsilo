package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.YocReport
import com.pirlruc.finsilo.ui.formatPercent

@Composable
internal fun YocCard(yoc: List<YocReport>) {
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
