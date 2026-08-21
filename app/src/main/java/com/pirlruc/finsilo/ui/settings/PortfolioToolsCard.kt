package com.pirlruc.finsilo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun PortfolioToolsCard(state: PortfolioToolsUiState, actions: PortfolioToolsActions) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val create = rememberBackupCreateLauncher(context, state, actions) { pending.also { pending = null } }
    val open = rememberBackupOpenLauncher(context, actions)
    LaunchBackupPicker(state.pendingExportName, actions, create)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TaxExportCard(state, actions) { name, producer ->
            producer { bytes ->
                pending = bytes
                launchCreate(create, name, actions) { pending = null }
            }
        }
        BackupExportCard(state, actions) { launchOpen(open, actions) }
        RestoreConfirmIfNeeded(state.confirmRestore, actions)
        ToolMessages(state.status, state.error)
    }
}
