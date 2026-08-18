package com.pirlruc.finsilo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Harvest-vault palette: ink silo interior, celadon growth, grain gold.
 * Every Material 3 slot is set so chips/FABs never fall back to baseline purple.
 */
internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7ED4B5),
        onPrimary = Color(0xFF003828),
        primaryContainer = Color(0xFF0F4F3D),
        onPrimaryContainer = Color(0xFFA8F0D3),
        secondary = Color(0xFFE4B75A),
        onSecondary = Color(0xFF3F2E00),
        secondaryContainer = Color(0xFF5C4300),
        onSecondaryContainer = Color(0xFFFFDEA3),
        tertiary = Color(0xFFA8B8D4),
        onTertiary = Color(0xFF1C2B44),
        tertiaryContainer = Color(0xFF33415B),
        onTertiaryContainer = Color(0xFFD6E2FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0B1210),
        onBackground = Color(0xFFE6EDE8),
        surface = Color(0xFF111917),
        onSurface = Color(0xFFE6EDE8),
        surfaceVariant = Color(0xFF2E3935),
        onSurfaceVariant = Color(0xFFBFC9C3),
        outline = Color(0xFF89938E),
        outlineVariant = Color(0xFF3E4945),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFE6EDE8),
        inverseOnSurface = Color(0xFF2C322F),
        inversePrimary = Color(0xFF1B5C4A),
        surfaceDim = Color(0xFF0B1210),
        surfaceBright = Color(0xFF2A3631),
        surfaceContainerLowest = Color(0xFF080D0C),
        surfaceContainerLow = Color(0xFF141C1A),
        surfaceContainer = Color(0xFF19211F),
        surfaceContainerHigh = Color(0xFF232C29),
        surfaceContainerHighest = Color(0xFF2E3935),
        surfaceTint = Color(0xFF7ED4B5),
    )

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF1B5C4A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA8F0D3),
        onPrimaryContainer = Color(0xFF002116),
        secondary = Color(0xFF7C5E10),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDEA3),
        onSecondaryContainer = Color(0xFF271900),
        tertiary = Color(0xFF4A5B78),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD6E2FF),
        onTertiaryContainer = Color(0xFF041B33),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF4F6F3),
        onBackground = Color(0xFF1A211E),
        surface = Color(0xFFF7F9F6),
        onSurface = Color(0xFF1A211E),
        surfaceVariant = Color(0xFFDEE3DD),
        onSurfaceVariant = Color(0xFF3E4945),
        outline = Color(0xFF6F7A75),
        outlineVariant = Color(0xFFBEC9C3),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF2C322F),
        inverseOnSurface = Color(0xFFEDF2ED),
        inversePrimary = Color(0xFF7ED4B5),
        surfaceDim = Color(0xFFD5DAD6),
        surfaceBright = Color(0xFFF7F9F6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F3EF),
        surfaceContainer = Color(0xFFEAEEE9),
        surfaceContainerHigh = Color(0xFFE4E9E3),
        surfaceContainerHighest = Color(0xFFDEE3DD),
        surfaceTint = Color(0xFF1B5C4A),
    )
