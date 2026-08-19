package com.pirlruc.finsilo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val FinsiloTypography =
    Typography().let { base ->
        base.copy(
            headlineSmall =
            base.headlineSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            ),
            titleLarge =
            base.titleLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            ),
            titleMedium =
            base.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
            ),
            labelLarge =
            base.labelLarge.copy(
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.4.sp,
            ),
        )
    }
