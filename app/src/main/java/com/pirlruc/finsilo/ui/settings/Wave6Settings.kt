package com.pirlruc.finsilo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.R
import com.pirlruc.finsilo.domain.fundamentals.OpenBbFundamentals

@Composable
internal fun ExtrasSettingsCard(container: AppContainer) {
    val dynamic by container.appearance.dynamicColor.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(container.keys.openBbBaseUrl().orEmpty()) }
    var token by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    val httpsError = stringResource(R.string.openbb_https)
    Column {
        Text(stringResource(R.string.openbb_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.openbb_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.openbb_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.openbb_token)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            val host = if (url.isBlank()) null else OpenBbFundamentals.host(url)
            if (url.isNotBlank() && host == null) {
                rejected = true
            } else {
                host?.let { com.pirlruc.finsilo.data.remote.MarketHttpsPolicy.allowHost(it) }
                container.keys.setOpenBb(url, token)
                rejected = false
            }
        }) { Text(stringResource(R.string.openbb_save)) }
        if (rejected) Text(httpsError, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.dynamic_color_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked = dynamic, onCheckedChange = container.appearance::setDynamicColor)
    }
}

@Composable
internal fun WebDavConflictDialog(visible: Boolean, actions: PortfolioToolsActions) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.webdav_conflict_title)) },
        text = { Text(stringResource(R.string.webdav_conflict_body), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = actions.onDownloadRemoteBackup) { Text(stringResource(R.string.webdav_download)) } },
        dismissButton = { TextButton(onClick = actions.onKeepLocalBackup) { Text(stringResource(R.string.webdav_keep)) } },
    )
}
