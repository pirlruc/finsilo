package com.pirlruc.finsilo.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Material You switch. Off keeps the existing light and dark schemes. */
class AppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val dynamic = MutableStateFlow(prefs.getBoolean(KEY, false))
    val dynamicColor: StateFlow<Boolean> = dynamic.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean(KEY, enabled) }
        dynamic.value = enabled
    }

    companion object {
        private const val FILE = "finsilo_ui"
        private const val KEY = "dynamic_color"
    }
}
