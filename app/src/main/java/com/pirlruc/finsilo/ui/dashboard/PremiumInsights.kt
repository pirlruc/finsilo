package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.entitlement.Entitlements
import com.pirlruc.finsilo.domain.entitlement.PremiumFeature
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.DividendReinvestment
import com.pirlruc.finsilo.domain.performance.FirePlanner
import com.pirlruc.finsilo.domain.performance.RebalanceSimulator
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatSignedEur
import java.math.BigDecimal
import java.time.LocalDate

@Composable
internal fun PremiumInsightCards(
    slices: List<AllocationSlice>,
    total: BigDecimal,
    transactions: List<Transaction>,
    asOf: LocalDate,
    entitlements: Entitlements,
) {
    if (entitlements.allows(PremiumFeature.REBALANCE)) RebalanceSimulatorCard(slices)
    if (entitlements.allows(PremiumFeature.FIRE_DRIP)) FireDripCard(transactions, total, asOf)
}

@Composable
internal fun RebalanceSimulatorCard(slices: List<AllocationSlice>) {
    var buyOnly by rememberSaveable { mutableStateOf(false) }
    val trades = RebalanceSimulator.trades(values(slices), targets(slices), buyOnly, BigDecimal.ZERO)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Rebalance simulator", style = MaterialTheme.typography.titleMedium)
            Text("Exact EUR trades to the target weights. Not financial advice.", style = MaterialTheme.typography.bodySmall)
            Switch(checked = buyOnly, onCheckedChange = { buyOnly = it })
            Text(if (buyOnly) "Buy only" else "Buys and sells", style = MaterialTheme.typography.bodySmall)
            if (trades.isEmpty()) {
                Text("No target weights to trade.", style = MaterialTheme.typography.bodySmall)
            }
            trades.forEach { trade -> Text("${trade.id} ${formatSignedEur(trade.amountEur)}") }
        }
    }
}

@Composable
internal fun FireDripCard(transactions: List<Transaction>, portfolioEur: BigDecimal, asOf: LocalDate) {
    val plan = FirePlanner.plan(portfolioEur, BigDecimal("24000"), BigDecimal("0.04"), BigDecimal("6000"), BigDecimal("0.05"))
    val drip = transactions.filter { it.type == TransactionType.DIVIDEND }.groupBy { it.assetId }.values.mapNotNull { rows ->
        DividendReinvestment.project(rows.map { it.date }, rows.maxBy { it.date }.notionalEur, asOf)
    }
    val income = drip.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.forwardIncomeEur) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("FIRE and DRIP", style = MaterialTheme.typography.titleMedium)
            Text(FirePlanner.NOTE, style = MaterialTheme.typography.bodySmall)
            Text(
                plan?.let { "FIRE ${formatEur(it.fireNumberEur)} · years ${it.years?.toPlainString() ?: "n/a"}" } ?: "FIRE unavailable",
            )
            Text(if (drip.isEmpty()) "No DRIP projection" else "DRIP forward ${formatEur(income)}")
        }
    }
}

private fun values(slices: List<AllocationSlice>): Map<String, BigDecimal> = slices.associate { it.assetType.name to it.valueEur }

private fun targets(slices: List<AllocationSlice>): Map<String, BigDecimal> =
    slices.mapNotNull { slice -> slice.targetPercent?.let { slice.assetType.name to it } }.toMap()
