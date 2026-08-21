package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import com.pirlruc.finsilo.ui.alerts.RatingLevelColumn
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatNative
import com.pirlruc.finsilo.ui.formatSignedEur
import com.pirlruc.finsilo.ui.theme.label
import java.math.BigDecimal

@Composable
internal fun HoldingsCard(
    holdings: List<HoldingValuation>,
    cashEur: BigDecimal,
    thresholds: Map<String, PriceAlertThreshold>,
    ratingPrefs: Map<String, RatingAlertPref>,
    onSaveThreshold: (PriceAlertThreshold) -> Unit,
    onSaveRating: (RatingAlertPref) -> Unit,
    onSaveInstrument: (Asset) -> Unit,
) {
    var alerting by remember { mutableStateOf<HoldingValuation?>(null) }
    var editing by remember { mutableStateOf<HoldingValuation?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Holdings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "EUR value is the ledger NAV. Native quotes are display-only and still convert with stored FX.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            holdings.forEach { holding ->
                HoldingRow(
                    holding = holding,
                    threshold = thresholds[holding.asset.id],
                    onAlert = { alerting = holding },
                    onEdit = { editing = holding },
                )
            }
            if (cashEur.signum() > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash", fontWeight = FontWeight.Medium)
                    Text(formatEur(cashEur), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    val alertTarget = alerting
    if (alertTarget != null) {
        HoldingAlertDialog(
            holding = alertTarget,
            existing = thresholds[alertTarget.asset.id],
            ratingPref = ratingPrefs[alertTarget.asset.id],
            onDismiss = { alerting = null },
            onSaveThreshold = { threshold ->
                onSaveThreshold(threshold)
                alerting = null
            },
            onSaveRating = { pref ->
                onSaveRating(pref)
                alerting = null
            },
        )
    }
    val editTarget = editing
    if (editTarget != null) {
        InstrumentEditDialog(
            asset = editTarget.asset,
            onDismiss = { editing = null },
            onSave = { asset ->
                onSaveInstrument(asset)
                editing = null
            },
        )
    }
}

@Composable
private fun HoldingRow(holding: HoldingValuation, threshold: PriceAlertThreshold?, onAlert: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(holding.asset.symbol, fontWeight = FontWeight.SemiBold)
                Text(
                    "${holding.asset.name} · ${holding.asset.assetType.label()} · qty ${holding.quantity.stripTrailingZeros().toPlainString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val native = holding.priceNative
                val currency = holding.quoteCurrency
                if (native != null && currency != null && currency != Currency.EUR) {
                    Text(
                        "Native ${formatNative(native, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatEur(holding.valueEur), fontWeight = FontWeight.SemiBold)
                Text(formatSignedEur(holding.unrealizedPnlEur), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = onAlert) { Text(if (threshold == null) "Alert" else "Alert on") }
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
            }
        }
    }
}

@Composable
private fun HoldingAlertDialog(
    holding: HoldingValuation,
    existing: PriceAlertThreshold?,
    ratingPref: RatingAlertPref?,
    onDismiss: () -> Unit,
    onSaveThreshold: (PriceAlertThreshold) -> Unit,
    onSaveRating: (RatingAlertPref) -> Unit,
) {
    var eur by remember { mutableStateOf(existing?.eurLevel?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var percent by remember { mutableStateOf(existing?.percentMove?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var levels by remember {
        mutableStateOf(RatingAlertPref.effective(ratingPref, RatingAlertScope.HOLDING))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${holding.asset.symbol} alerts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Evaluated on stored closes after sync. No extra Yahoo feed.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(eur, { eur = it }, label = { Text("EUR level") }, singleLine = true)
                OutlinedTextField(percent, { percent = it }, label = { Text("Day move %") }, singleLine = true)
                RatingLevelColumn(levels) { rating, checked ->
                    levels = if (checked) levels + rating else levels - rating
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveThreshold(
                        PriceAlertThreshold(
                            assetId = holding.asset.id,
                            eurLevel = parseDecimal(eur),
                            percentMove = parseDecimal(percent),
                        ),
                    )
                    onSaveRating(RatingAlertPref(holding.asset.id, RatingAlertScope.HOLDING, levels))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InstrumentEditDialog(asset: Asset, onDismiss: () -> Unit, onSave: (Asset) -> Unit) {
    var name by remember { mutableStateOf(asset.name) }
    var quote by remember { mutableStateOf(asset.quoteSymbol.orEmpty()) }
    var isin by remember { mutableStateOf(asset.isin.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${asset.symbol}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Changing the quote symbol drops stored daily bars for this holding and refetches on the next sync.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(quote, { quote = it }, label = { Text("Quote symbol") }, singleLine = true)
                OutlinedTextField(isin, { isin = it }, label = { Text("ISIN") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        asset.copy(
                            name = name.trim().ifBlank { asset.symbol },
                            quoteSymbol = quote.trim().ifBlank { null },
                            isin = isin.trim().ifBlank { null },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
