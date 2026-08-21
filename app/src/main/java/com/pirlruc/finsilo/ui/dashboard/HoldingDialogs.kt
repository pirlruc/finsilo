package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import com.pirlruc.finsilo.ui.alerts.RatingLevelColumn

@Composable
internal fun HoldingAlertDialog(
    holding: HoldingValuation,
    existing: PriceAlertThreshold?,
    ratingPref: RatingAlertPref?,
    onDismiss: () -> Unit,
    onSaveThreshold: (PriceAlertThreshold) -> Unit,
    onSaveRating: (RatingAlertPref) -> Unit,
) {
    var eur by remember { mutableStateOf(existing?.eurLevel?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var percent by remember { mutableStateOf(existing?.percentMove?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var levels by remember { mutableStateOf(RatingAlertPref.effective(ratingPref, RatingAlertScope.HOLDING)) }
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
internal fun InstrumentEditDialog(asset: Asset, onDismiss: () -> Unit, onSave: (Asset) -> Unit) {
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
