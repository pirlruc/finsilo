package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Transaction
import java.time.LocalDate

@Composable
fun ActivityLedger(
    transactions: List<Transaction>,
    assets: List<Asset>,
    onAdd: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (String, LocalDate) -> Unit,
) {
    val names = assets.associate { it.id to it.symbol }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add transaction") }
        transactions.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.sequence }).forEach { tx ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${tx.date} ${tx.type} ${names[tx.assetId].orEmpty()}",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEdit(tx) }) { Text("Edit") }
                TextButton(onClick = { onDelete(tx.id, tx.date) }) { Text("Delete") }
            }
        }
    }
}
