package com.pirlruc.finsilo.ui.lock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val RECOVERY_CLIPBOARD_MS: Long = 60_000L

@Composable
internal fun rememberCopyRecovery(): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { code -> copyRecovery(context, scope, code) }
    }
}

@Composable
internal fun CopyRecoveryButton(code: String, enabled: Boolean) {
    val copy = rememberCopyRecovery()
    TextButton(onClick = { copy(code) }, enabled = enabled && code.isNotBlank()) {
        Text("Copy recovery code")
    }
}

private fun copyRecovery(context: Context, scope: CoroutineScope, code: String) {
    if (code.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("FinSilo recovery", code))
    scope.launch {
        delay(RECOVERY_CLIPBOARD_MS)
        if (clipText(clipboard) == code) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

private fun clipText(clipboard: ClipboardManager): String? {
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).text?.toString()
}
