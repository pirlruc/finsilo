package com.pirlruc.finsilo.ui.valuation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.R
import com.pirlruc.finsilo.data.FundamentalLoader
import com.pirlruc.finsilo.data.remote.HttpGetClient
import com.pirlruc.finsilo.data.remote.OpenBbFundamentalsClient
import com.pirlruc.finsilo.domain.fundamentals.FundamentalView
import com.pirlruc.finsilo.domain.fundamentals.ValuationNote
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.ui.formatPercent
import java.math.BigDecimal

class ValuationAccess(private val container: AppContainer) {
    suspend fun load(asset: Asset, price: BigDecimal?): FundamentalView = FundamentalLoader(
        container.repository.fundamentalCache(),
        container.marketFeed,
        OpenBbFundamentalsClient(HttpGetClient(), container.keys),
    ).load(asset, price)
}

val LocalValuation = staticCompositionLocalOf<ValuationAccess?> { null }

@Composable
internal fun ValuationSlot(asset: Asset, price: BigDecimal?) {
    val access = LocalValuation.current
    var view by remember(asset.id) { mutableStateOf<FundamentalView?>(null) }
    LaunchedEffect(asset.id, price, access) {
        view = access?.load(asset, price)
    }
    view?.let { ValuationCard(it) }
}

@Composable
internal fun ValuationCard(view: FundamentalView) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.valuation_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(note(view.note)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (view.note == ValuationNote.RATIOS) {
                Text(line(R.string.valuation_pe, view.metrics.pe), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_peg, view.metrics.peg), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_fcf, view.metrics.fcfYield, percent = true), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_roe, view.metrics.roe, percent = true), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_roic, view.metrics.roic, percent = true), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_de, view.metrics.debtToEquity), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_ev, view.metrics.evToEbit), color = MaterialTheme.colorScheme.onSurface)
                Text(line(R.string.valuation_cover, view.metrics.interestCoverage), color = MaterialTheme.colorScheme.onSurface)
            }
            val source = view.source.ifBlank { stringResource(R.string.valuation_source_unknown) }
            val asOf = view.asOf?.toString() ?: stringResource(R.string.valuation_as_of_unknown)
            Text(
                stringResource(R.string.valuation_source_line, source, asOf),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun line(label: Int, value: BigDecimal?, percent: Boolean = false): String {
    val shown = when {
        value == null -> stringResource(R.string.valuation_undefined)
        percent -> formatPercent(value)
        else -> value.stripTrailingZeros().toPlainString()
    }
    return stringResource(label, shown)
}

private fun note(note: ValuationNote): Int = when (note) {
    ValuationNote.RATIOS -> R.string.valuation_explain
    ValuationNote.NOT_APPLICABLE -> R.string.valuation_not_applicable
    ValuationNote.MISSING -> R.string.valuation_missing
    ValuationNote.BUDGET -> R.string.valuation_budget
}
