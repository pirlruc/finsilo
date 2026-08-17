package com.pirlruc.finsilo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.ui.dashboard.DashboardRoute
import com.pirlruc.finsilo.ui.dashboard.DashboardViewModel
import com.pirlruc.finsilo.ui.ledger.LedgerEntryRoute
import com.pirlruc.finsilo.ui.ledger.LedgerEntryViewModel
import com.pirlruc.finsilo.ui.settings.TargetSettingsRoute
import com.pirlruc.finsilo.ui.settings.TargetSettingsViewModel

private enum class AppScreen {
    DASHBOARD,
    LEDGER,
    SETTINGS,
}

@Composable
fun FinsiloApp(container: AppContainer) {
    RequestNotificationPermission()
    var screen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
    val dashboard: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    when (screen) {
        AppScreen.DASHBOARD ->
            DashboardRoute(
                viewModel = dashboard,
                onAddTransaction = { screen = AppScreen.LEDGER },
                onOpenSettings = { screen = AppScreen.SETTINGS },
            )
        AppScreen.LEDGER -> {
            val ledger: LedgerEntryViewModel = viewModel(factory = LedgerEntryViewModel.factory(container))
            LedgerEntryRoute(
                viewModel = ledger,
                onClose = {
                    screen = AppScreen.DASHBOARD
                    dashboard.refresh()
                },
            )
        }
        AppScreen.SETTINGS -> {
            val settings: TargetSettingsViewModel = viewModel(factory = TargetSettingsViewModel.factory(container))
            TargetSettingsRoute(
                viewModel = settings,
                onClose = {
                    screen = AppScreen.DASHBOARD
                    dashboard.refresh()
                },
            )
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
