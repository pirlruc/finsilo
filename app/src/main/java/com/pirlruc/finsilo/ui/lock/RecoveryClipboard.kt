package com.pirlruc.finsilo.ui.lock

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal const val RECOVERY_CLIPBOARD_MS: Long = 60_000L

@Composable
internal fun rememberCopyRecovery(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { copyRecoveryAction(context) }
}

@Composable
internal fun RecoveryCodeRow(code: String, enabled: Boolean) {
    val copy = rememberCopyRecovery()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = code,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        IconButton(onClick = { copy(code) }, enabled = enabled && code.isNotBlank()) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy recovery code")
        }
    }
}

private val clipboardClearHandler = Handler(Looper.getMainLooper())

private fun copyRecoveryAction(context: Context): (String) -> Unit {
    val app = context.applicationContext
    return { code ->
        if (code.isNotBlank()) {
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(sensitiveClip(code))
            clipboardClearHandler.removeCallbacksAndMessages(null)
            clipboardClearHandler.postDelayed({ clearClip(clipboard) }, RECOVERY_CLIPBOARD_MS)
        }
    }
}

private fun sensitiveClip(code: String): ClipData {
    val clip = ClipData.newPlainText("FinSilo recovery", code)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras =
            PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    return clip
}

private fun clearClip(clipboard: ClipboardManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
    } else {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
