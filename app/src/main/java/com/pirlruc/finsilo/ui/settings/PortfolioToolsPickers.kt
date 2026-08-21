package com.pirlruc.finsilo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun rememberBackupCreateLauncher(
    context: Context,
    state: PortfolioToolsUiState,
    actions: PortfolioToolsActions,
    takePending: () -> ByteArray?,
): ActivityResultLauncher<String> = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
    actions.onPickerBusy(false)
    val bytes = takePending() ?: state.pendingExport
    val written = writePicked(context, uri, bytes)
    if (state.pendingExportName != null) actions.onExportConsumed(written)
}

@Composable
internal fun rememberBackupOpenLauncher(context: Context, actions: PortfolioToolsActions): ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        actions.onPickerBusy(false)
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input -> actions.onRestoreBackup(input.readBytes()) }
    }

@Composable
internal fun LaunchBackupPicker(fileName: String?, actions: PortfolioToolsActions, create: ActivityResultLauncher<String>) {
    LaunchedEffect(fileName) {
        if (fileName == null) return@LaunchedEffect
        launchCreate(create, fileName, actions) {}
    }
}

internal fun launchCreate(create: ActivityResultLauncher<String>, name: String, actions: PortfolioToolsActions, onFail: () -> Unit) {
    actions.onPickerBusy(true)
    runCatching { create.launch(name) }.onFailure { error ->
        actions.onPickerBusy(false)
        onFail()
        actions.onExportLaunchFailed(error.message ?: "Could not open the file picker.")
    }
}

internal fun launchOpen(open: ActivityResultLauncher<Array<String>>, actions: PortfolioToolsActions) {
    actions.onPickerBusy(true)
    runCatching { open.launch(arrayOf("*/*")) }.onFailure { error ->
        actions.onPickerBusy(false)
        actions.onExportLaunchFailed(error.message ?: "Could not open the file picker.")
    }
}

internal fun writePicked(context: Context, uri: Uri?, bytes: ByteArray?): Boolean =
    uri != null && bytes != null && writeUri(context, uri, bytes)

private fun writeUri(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
}.getOrDefault(false)
