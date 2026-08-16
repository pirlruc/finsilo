package com.pirlruc.finsilo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pirlruc.finsilo.ui.dashboard.DashboardRoute
import com.pirlruc.finsilo.ui.dashboard.DashboardViewModel
import com.pirlruc.finsilo.ui.theme.FinsiloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FinsiloApplication).container
        setContent {
            FinsiloTheme {
                val viewModel: DashboardViewModel =
                    viewModel(factory = DashboardViewModel.factory(container))
                DashboardRoute(viewModel)
            }
        }
    }
}
