package com.pirlruc.finsilo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
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
        enableEdgeToEdge()
        val container = (application as FinsiloApplication).container
        setContent {
            FinsiloTheme {
                FinsiloApp(container)
            }
        }
    }
}
