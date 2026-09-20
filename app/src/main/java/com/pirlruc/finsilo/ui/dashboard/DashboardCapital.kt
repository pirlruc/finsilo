package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.BrokerCapital
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatSignedEur

@Composable
internal fun CapitalCard(allocation: AllocationReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Capital", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Money put in is deposits minus withdrawals. Uninvested cash is leftover EUR after that broker's trades; " +
                    "a later import can spend cash you deposited at another broker. " +
                    "Cash interest is sweep income on uninvested cash, not extra deposits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CapitalLine("Money put in", formatEur(allocation.contributedEur))
            CapitalLine("Uninvested cash", formatEur(allocation.cashEur))
            CapitalLine(
                "Cash interest",
                formatSignedEur(allocation.cashInterestEur),
                signedAmountColor(allocation.cashInterestEur),
            )
            CapitalLine(
                "Gain / loss",
                formatSignedEur(allocation.totalGainEur),
                signedAmountColor(allocation.totalGainEur),
            )
            allocation.brokers.forEach { BrokerCapitalBlock(it) }
        }
    }
}

@Composable
private fun BrokerCapitalBlock(slice: BrokerCapital) {
    Text(slice.source.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CapitalLine("Money put in", formatEur(slice.contributedEur))
    CapitalLine("Uninvested cash", formatEur(slice.cashEur))
    CapitalLine(
        "Cash interest",
        formatSignedEur(slice.cashInterestEur),
        signedAmountColor(slice.cashInterestEur),
    )
    CapitalLine(
        "Gain / loss",
        formatSignedEur(slice.gainEur),
        signedAmountColor(slice.gainEur),
    )
}

@Composable
private fun CapitalLine(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
