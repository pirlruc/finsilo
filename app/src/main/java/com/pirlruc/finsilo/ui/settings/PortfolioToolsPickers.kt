package com.pirlruc.finsilo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PickedWritePlan(val bytes: ByteArray?, val consumeBackup: Boolean, val missingBackup: Boolean)

internal fun planPickedWrite(uriPresent: Boolean, pending: ByteArray?, backup: ByteArray?, backupNamed: Boolean): PickedWritePlan {
    if (!uriPresent) return PickedWritePlan(bytes = null, consumeBackup = backupNamed, missingBackup = false)
    val bytes = pending ?: backup
    if (bytes == null) return PickedWritePlan(bytes = null, consumeBackup = false, missingBackup = backupNamed)
    return PickedWritePlan(bytes = bytes, consumeBackup = backupNamed, missingBackup = false)
}

internal object BoundedBytes {
    const val MAX_BACKUP_BYTES: Int = 16 * 1024 * 1024

    fun read(stream: InputStream, maxBytes: Int = MAX_BACKUP_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("Backup file is too large.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

@Composable
internal fun rememberBackupCreateLauncher(
    context: Context,
    state: PortfolioToolsUiState,
    actions: PortfolioToolsActions,
    takePending: () -> ByteArray?,
): ActivityResultLauncher<String> {
    val export by rememberUpdatedState(state.pendingExport)
    val backupNamed by rememberUpdatedState(state.pendingExportName != null)
    val actionsRef by rememberUpdatedState(actions)
    val take by rememberUpdatedState(takePending)
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        actionsRef.onPickerBusy(false)
        finishCreateDocument(context, uri, take(), export, backupNamed, actionsRef, scope)
    }
}

internal fun finishCreateDocument(
    context: Context,
    uri: Uri?,
    pending: ByteArray?,
    backup: ByteArray?,
    backupNamed: Boolean,
    actions: PortfolioToolsActions,
    scope: CoroutineScope,
) {
    val plan = planPickedWrite(uri != null, pending, backup, backupNamed)
    val bytes = plan.bytes
    when {
        plan.missingBackup -> actions.onExportLaunchFailed("Backup data was lost. Try export again.")
        bytes == null -> if (plan.consumeBackup) actions.onExportConsumed(false)
        else -> writeCreateDocument(context, uri, bytes, plan.consumeBackup, actions, scope)
    }
}

private fun writeCreateDocument(
    context: Context,
    uri: Uri?,
    bytes: ByteArray,
    consumeBackup: Boolean,
    actions: PortfolioToolsActions,
    scope: CoroutineScope,
) {
    scope.launch {
        val written = withContext(Dispatchers.IO) { writePicked(context, uri, bytes) }
        reportCreateWrite(written, consumeBackup, actions)
    }
}

private fun reportCreateWrite(written: Boolean, consumeBackup: Boolean, actions: PortfolioToolsActions) {
    if (written && consumeBackup) {
        actions.onExportConsumed(true)
        return
    }
    if (!written) {
        actions.onExportLaunchFailed(if (consumeBackup) "Could not write the backup file." else "Could not write the export.")
    }
}

@Composable
internal fun rememberBackupOpenLauncher(context: Context, actions: PortfolioToolsActions): ActivityResultLauncher<Array<String>> {
    val scope = rememberCoroutineScope()
    val actionsRef by rememberUpdatedState(actions)
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        actionsRef.onPickerBusy(false)
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BoundedBytes.read(it) }
                }
            }.getOrElse { error ->
                actionsRef.onExportLaunchFailed(error.message ?: "Could not read the backup file.")
                return@launch
            }
            if (bytes == null) {
                actionsRef.onExportLaunchFailed("Could not read the backup file.")
            } else {
                actionsRef.onRestoreBackup(bytes)
            }
        }
    }
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
