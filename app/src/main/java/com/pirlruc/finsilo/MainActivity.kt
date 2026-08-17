package com.pirlruc.finsilo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pirlruc.finsilo.ui.FinsiloApp
import com.pirlruc.finsilo.ui.theme.FinsiloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FinsiloApplication).container
        setContent {
            FinsiloTheme {
                FinsiloApp(container)
            }
        }
    }
}
