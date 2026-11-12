package com.pirlruc.finsilo.ui.lock

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Keeps FLAG_SECURE on dialog windows that draw ledger or PIN text. */
object SecureFlagPolicy {
    @Composable
    fun SecureOn() {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { }
        }
    }
}
