package com.pirlruc.finsilo

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.ui.FinsiloApp
import com.pirlruc.finsilo.ui.theme.FinsiloTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        window.decorView.filterTouchesWhenObscured = true
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        enableEdgeToEdge()
        val container = (application as FinsiloApplication).container
        setContent {
            val dynamicColor by container.appearance.dynamicColor.collectAsStateWithLifecycle()
            FinsiloTheme(dynamicColor = dynamicColor) {
                FinsiloApp(container)
            }
        }
    }
}
