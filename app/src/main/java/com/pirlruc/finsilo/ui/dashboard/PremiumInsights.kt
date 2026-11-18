package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.DividendReinvestment
import com.pirlruc.finsilo.domain.performance.FireAssumptions
import com.pirlruc.finsilo.domain.performance.FirePlanner
import com.pirlruc.finsilo.domain.performance.RebalanceBook
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
    holdings: List<HoldingValuation> = emptyList(),
    fire: FireAssumptions = FireAssumptions.defaults(),
    onFire: (FireAssumptions) -> Unit = {},
) {
    if (entitlements.allows(PremiumFeature.REBALANCE)) RebalanceSimulatorCard(slices, holdings)
    if (entitlements.allows(PremiumFeature.FIRE_DRIP)) FireDripCard(transactions, total, asOf, fire, onFire)
}

@Composable
internal fun RebalanceSimulatorCard(slices: List<AllocationSlice>, holdings: List<HoldingValuation> = emptyList()) {
    var buyOnly by rememberSaveable { mutableStateOf(false) }
    val book = RebalanceBook.valuesAndTargets(holdings, slices)
    val trades = RebalanceSimulator.trades(book.first, book.second, buyOnly, BigDecimal.ZERO)
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
internal fun FireDripCard(
    transactions: List<Transaction>,
    portfolioEur: BigDecimal,
    asOf: LocalDate,
    assumptions: FireAssumptions = FireAssumptions.defaults(),
    onFire: (FireAssumptions) -> Unit = {},
) {
    val plan = FirePlanner.plan(
        portfolioEur,
        assumptions.annualExpensesEur,
        assumptions.withdrawalRate,
        assumptions.annualSavingsEur,
        assumptions.annualReturn,
    )
    val drip = transactions.filter { it.type == TransactionType.DIVIDEND }.groupBy { it.assetId }.values.mapNotNull { rows ->
        DividendReinvestment.project(rows.map { it.date }, rows.maxBy { it.date }.notionalEur, asOf)
    }
    val income = drip.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.forwardIncomeEur) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("FIRE and DRIP", style = MaterialTheme.typography.titleMedium)
            Text(FirePlanner.NOTE, style = MaterialTheme.typography.bodySmall)
            FireField("Annual expenses EUR", assumptions.annualExpensesEur) { amount ->
                onFire(assumptions.copy(annualExpensesEur = amount))
            }
            FireField("Withdrawal rate", assumptions.withdrawalRate) { amount ->
                onFire(assumptions.copy(withdrawalRate = amount))
            }
            FireField("Annual savings EUR", assumptions.annualSavingsEur) { amount ->
                onFire(assumptions.copy(annualSavingsEur = amount))
            }
            FireField("Annual return", assumptions.annualReturn) { amount ->
                onFire(assumptions.copy(annualReturn = amount))
            }
            Text(
                plan?.let { "FIRE ${formatEur(it.fireNumberEur)} · years ${it.years?.toPlainString() ?: "n/a"}" } ?: "FIRE unavailable",
            )
            Text(if (drip.isEmpty()) "No DRIP projection" else "DRIP forward ${formatEur(income)}")
        }
    }
}

@Composable
private fun FireField(label: String, value: BigDecimal, onValue: (BigDecimal) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value.toPlainString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            next.toBigDecimalOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
