package com.pirlruc.finsilo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.AssetType

fun AssetType.chartColor(): Color = when (this) {
    AssetType.STOCK -> Color(0xFF1B5C4A)
    AssetType.ETF -> Color(0xFF3D8F74)
    AssetType.CRYPTO -> Color(0xFFE4B75A)
    AssetType.DEPOSIT -> Color(0xFF4A5B78)
    AssetType.PPR -> Color(0xFF7A5E8B)
    AssetType.CT -> Color(0xFF1F4F55)
    AssetType.COMMODITY -> Color(0xFFB07040)
    AssetType.CASH -> Color(0xFF6E7B76)
}

fun AssetType.label(): String = when (this) {
    AssetType.STOCK -> "Stocks"
    AssetType.ETF -> "ETFs"
    AssetType.CRYPTO -> "Crypto"
    AssetType.DEPOSIT -> "Deposits"
    AssetType.PPR -> "PPR"
    AssetType.CT -> "CTs"
    AssetType.COMMODITY -> "Commodities"
    AssetType.CASH -> "Cash"
}

private val FinsiloShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
fun FinsiloTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = FinsiloTypography,
        shapes = FinsiloShapes,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background, content = content)
    }
}
