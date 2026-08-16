package com.pirlruc.finsilo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pirlruc.finsilo.domain.model.AssetType

private val Green = Color(0xFF1F6B54)
private val GreenDark = Color(0xFF0F3D32)
private val Cream = Color(0xFFF4F7F4)
private val Gold = Color(0xFFC4A35A)

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8FCBB3),
        onPrimary = GreenDark,
        primaryContainer = Green,
        onPrimaryContainer = Cream,
        secondary = Gold,
        onSecondary = GreenDark,
        background = Color(0xFF101614),
        onBackground = Cream,
        surface = Color(0xFF18211D),
        onSurface = Cream,
        surfaceVariant = Color(0xFF24302B),
        onSurfaceVariant = Color(0xFFC5D5CC),
        error = Color(0xFFE07A7A),
        outline = Color(0xFF3E534B),
    )

private val LightColors =
    lightColorScheme(
        primary = Green,
        onPrimary = Cream,
        primaryContainer = Color(0xFFD6EADF),
        onPrimaryContainer = GreenDark,
        secondary = Gold,
        onSecondary = GreenDark,
        background = Cream,
        onBackground = GreenDark,
        surface = Color(0xFFFFFFFF),
        onSurface = GreenDark,
        surfaceVariant = Color(0xFFE3EEE8),
        onSurfaceVariant = Color(0xFF3E534B),
        error = Color(0xFFB3261E),
        outline = Color(0xFF8AA399),
    )

fun AssetType.chartColor(): Color =
    when (this) {
        AssetType.STOCK -> Color(0xFF2F6F5E)
        AssetType.ETF -> Color(0xFF5B9A78)
        AssetType.CRYPTO -> Color(0xFFC4A35A)
        AssetType.DEPOSIT -> Color(0xFF4A6FA5)
        AssetType.PPR -> Color(0xFF8B5E83)
        AssetType.CT -> Color(0xFF1F4F55)
        AssetType.COMMODITY -> Color(0xFFB07040)
        AssetType.CASH -> Color(0xFF7A8B7A)
    }

fun AssetType.label(): String =
    when (this) {
        AssetType.STOCK -> "Stocks"
        AssetType.ETF -> "ETFs"
        AssetType.CRYPTO -> "Crypto"
        AssetType.DEPOSIT -> "Deposits"
        AssetType.PPR -> "PPR"
        AssetType.CT -> "CTs"
        AssetType.COMMODITY -> "Commodities"
        AssetType.CASH -> "Cash"
    }

@Composable
fun FinsiloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
