package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SecuritySettingsCard(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    newRecoveryCode: String?,
    status: String?,
    error: String?,
    onToggleBiometric: (Boolean) -> Unit,
    onRotateRecovery: () -> Unit,
    onDismissRecovery: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "PIN is required on launch. Recovery code resets the PIN. Biometrics unlock this session only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (biometricAvailable) {
                Button(onClick = { onToggleBiometric(!biometricEnabled) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (biometricEnabled) "Disable biometric unlock" else "Enable biometric unlock")
                }
            }
            Button(onClick = onRotateRecovery, modifier = Modifier.fillMaxWidth()) {
                Text("Generate a new recovery code")
            }
            if (newRecoveryCode != null) {
                Text(newRecoveryCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismissRecovery) { Text("I saved the new code") }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
