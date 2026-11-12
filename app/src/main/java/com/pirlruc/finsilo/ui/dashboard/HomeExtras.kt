package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.GetRealizedGainsUseCase
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatSignedEur
import java.math.BigDecimal

@Composable
internal fun HoldingsBrowser(
    holdings: List<HoldingValuation>,
    cashEur: BigDecimal,
    thresholds: Map<String, PriceAlertThreshold>,
    ratingPrefs: Map<String, RatingAlertPref>,
    actions: HoldingActions,
    transactions: List<Transaction>,
    assets: List<Asset>,
) {
    var query by remember { mutableStateOf("") }
    var bySymbol by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val filtered = holdings.filter { holding ->
        val asset = holding.asset
        query.isBlank() || asset.symbol.contains(query, ignoreCase = true) || asset.name.contains(query, ignoreCase = true)
    }
    val sorted = if (bySymbol) filtered.sortedBy { it.asset.symbol } else filtered.sortedByDescending { it.valueEur }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search holdings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !bySymbol, onClick = { bySymbol = false }, label = { Text("Value") })
            FilterChip(selected = bySymbol, onClick = { bySymbol = true }, label = { Text("Symbol") })
        }
        HoldingsCard(
            sorted,
            cashEur,
            thresholds,
            ratingPrefs,
            actions.onSaveThreshold,
            actions.onSaveRating,
            actions.onSaveInstrument,
        )
        sorted.forEach { holding ->
            FilterChip(
                selected = selected == holding.asset.id,
                onClick = { selected = holding.asset.id },
                label = { Text(holding.asset.symbol) },
            )
        }
        val detail = sorted.firstOrNull { it.asset.id == selected }
        if (detail != null) {
            HoldingDetail(detail, transactions.filter { it.assetId == detail.asset.id })
        }
        if (assets.isEmpty()) {
            Text("Instruments load with the ledger.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HoldingDetail(holding: HoldingValuation, rows: List<Transaction>) {
    val lots = remember(rows) { PositionLedger().openLots(rows) }
    val dividends = rows.filter { it.type == TransactionType.DIVIDEND }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(holding.asset.name, style = MaterialTheme.typography.titleMedium)
            val price = holding.priceNative?.toPlainString() ?: "no close"
            Text("Latest price $price ${holding.quoteCurrency ?: ""}")
            Text("FIFO lots")
            lots.forEach { lot ->
                Text("${lot.acquiredDate}  ${lot.quantity.toPlainString()}  ${formatEur(lot.remainingCostEur)}")
            }
            Text("Dividends ${dividends.size}")
        }
    }
}

@Composable
internal fun RebalanceCard(slices: List<com.pirlruc.finsilo.domain.model.AllocationSlice>, total: BigDecimal) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Text("Rebalance", style = MaterialTheme.typography.titleMedium)
            slices.forEach { slice ->
                val target = slice.targetPercent
                if (target != null) {
                    val gap = target.subtract(slice.weightPercent)
                    val eur = total.multiply(gap).divide(BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                    Text("${slice.assetType} ${formatSignedEur(eur)}")
                }
            }
        }
    }
}

@Composable
internal fun MaisValiasPreview(transactions: List<Transaction>, assets: List<Asset>, year: Int) {
    val report = remember(transactions, assets, year) {
        GetRealizedGainsUseCase()(
            PortfolioSnapshot(assets, transactions, emptyList(), emptyList(), emptyList()),
            year,
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Text("Mais-valias $year", style = MaterialTheme.typography.titleMedium)
            Text("${report.lines.size} lots · ${formatEur(report.totalGainEur)}")
            Text("The tax file export stays in Settings.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
