package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.math.BigDecimal

@Composable
internal fun signedAmountColor(amount: BigDecimal): Color {
    val dark = isSystemInDarkTheme()
    return when {
        amount.signum() > 0 -> if (dark) Color(0xFF7ED4B5) else Color(0xFF157A4B)
        amount.signum() < 0 -> if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
        else -> MaterialTheme.colorScheme.onSurface
    }
}
