package com.pirlruc.finsilo.ui.settings

import com.pirlruc.finsilo.domain.model.AssetType

internal data class PortfolioToolsActions(
    val onYear: (String) -> Unit,
    val onRecovery: (String) -> Unit,
    val onExportCsv: ((ByteArray) -> Unit) -> Unit,
    val onExportPdf: ((ByteArray) -> Unit) -> Unit,
    val onExportBackup: ((ByteArray) -> Unit) -> Unit,
    val onRestoreBackup: (ByteArray) -> Unit,
    val onConfirmRestore: () -> Unit,
    val onCancelRestore: () -> Unit,
    val onPickerBusy: (Boolean) -> Unit,
)

internal data class LockSettingsActions(
    val onToggleBiometric: () -> Unit,
    val onRotateRecovery: () -> Unit,
    val onDismissRecovery: () -> Unit,
    val onConfirmSensitive: () -> Unit,
    val onCancelSensitive: () -> Unit,
    val onLockPin: (String) -> Unit,
)

internal data class TargetSettingsActions(
    val onClose: () -> Unit,
    val onWeight: (AssetType, String) -> Unit,
    val onSave: () -> Unit,
    val onImportCsvs: (List<String>) -> Unit,
    val onPickerBusy: (Boolean) -> Unit,
    val lock: LockSettingsActions,
    val tools: PortfolioToolsActions,
)
