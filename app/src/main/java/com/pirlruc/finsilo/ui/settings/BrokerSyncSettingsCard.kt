package com.pirlruc.finsilo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.data.security.DatabaseKeyStore

/** Read-only broker credentials and a WebDAV app password. Blank fields keep the stored value. */
@Composable
internal fun BrokerSyncSettingsCard(keys: DatabaseKeyStore) {
    var ibkrToken by remember { mutableStateOf("") }
    var ibkrQuery by remember { mutableStateOf("") }
    var krakenKey by remember { mutableStateOf("") }
    var krakenSecret by remember { mutableStateOf("") }
    var coinbaseName by remember { mutableStateOf("") }
    var coinbaseKey by remember { mutableStateOf("") }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Other brokers and WebDAV", style = MaterialTheme.typography.titleMedium)
            Text(
                "IBKR Flex, Kraken ledgers, and Coinbase fills are read-only. WebDAV stores the encrypted backup. Secrets stay on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
            SecretField("IBKR token", ibkrToken) { ibkrToken = it }
            SecretField("IBKR query id", ibkrQuery) { ibkrQuery = it }
            SecretField("Kraken key", krakenKey) { krakenKey = it }
            SecretField("Kraken secret", krakenSecret) { krakenSecret = it }
            SecretField("Coinbase key name", coinbaseName) { coinbaseName = it }
            SecretField("Coinbase key", coinbaseKey) { coinbaseKey = it }
            SecretField("WebDAV URL", webDavUrl) { webDavUrl = it }
            SecretField("WebDAV user", webDavUser) { webDavUser = it }
            SecretField("WebDAV app password", webDavPassword) { webDavPassword = it }
            Button(onClick = {
                val ok = keys.setIbkr(ibkrToken, ibkrQuery) &&
                    keys.setKraken(krakenKey, krakenSecret) &&
                    keys.setCoinbase(coinbaseName, coinbaseKey) &&
                    keys.setWebDav(webDavUrl, webDavUser, webDavPassword)
                message = if (ok) "Stored on this device" else "Could not store the settings"
            }) { Text("Save") }
            keys.brokerSyncStatus()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
