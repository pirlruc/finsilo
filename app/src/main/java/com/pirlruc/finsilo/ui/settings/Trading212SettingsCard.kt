package com.pirlruc.finsilo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.data.security.DatabaseKeyStore

/** Settings toggle for read-only Trading 212 sync. The secret is not shown again. */
@Composable
internal fun Trading212SettingsCard(keys: DatabaseKeyStore) {
    var enabled by remember { mutableStateOf(keys.trading212SyncEnabled()) }
    var apiKey by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val stored = keys.trading212Key() != null && keys.trading212Secret() != null
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trading 212", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only HTTP Basic sync of history. No orders. Leave a field blank to keep the stored value.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (stored) "Key and secret stored on this device" else "Sync after unlock")
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(apiKey, {
                apiKey = it
            }, label = {
                Text("API key")
            }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secret, {
                secret = it
            }, label = {
                Text("API secret")
            }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { message = save(keys, enabled, apiKey, secret) }) { Text("Save Trading 212") }
            keys.trading212Status()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun save(keys: DatabaseKeyStore, enabled: Boolean, apiKey: String, secret: String): String {
    val ok = keys.setTrading212SyncEnabled(enabled) && keys.setTrading212Credentials(apiKey, secret)
    return if (ok) "Trading 212 settings stored on this device" else "Could not store the Trading 212 settings"
}
