package com.pirlruc.finsilo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
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
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel
import com.pirlruc.finsilo.ui.ledger.LedgerEntryRoute
import com.pirlruc.finsilo.ui.ledger.LedgerEntryViewModel
import com.pirlruc.finsilo.ui.lock.AppLockGate
import com.pirlruc.finsilo.ui.lock.LockViewModel
import com.pirlruc.finsilo.ui.settings.TargetSettingsRoute
import com.pirlruc.finsilo.ui.settings.TargetSettingsViewModel
import com.pirlruc.finsilo.ui.watchlist.WatchlistRoute
import com.pirlruc.finsilo.ui.watchlist.WatchlistViewModel

private enum class AppScreen {
    DASHBOARD,
    LEDGER,
    SETTINGS,
    WATCHLIST,
}

@Composable
fun FinsiloApp(container: AppContainer) {
    val lock: LockViewModel = viewModel(factory = LockViewModel.factory(container))
    AppLockGate(lock) {
        RequestNotificationPermission(onPickerBusy = lock::setExternalUiActive)
        UnlockedApp(container, lock)
    }
}

@Composable
private fun UnlockedApp(container: AppContainer, lock: LockViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
    val dashboard: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val importer: BrokerImportViewModel = viewModel(factory = BrokerImportViewModel.factory(container))
    val ledger: LedgerEntryViewModel = viewModel(factory = LedgerEntryViewModel.factory(container))
    BackHandler(enabled = screen != AppScreen.DASHBOARD) {
        screen = AppScreen.DASHBOARD
        dashboard.refresh()
    }
    when (screen) {
        AppScreen.DASHBOARD ->
            DashboardRoute(
                viewModel = dashboard,
                importer = importer,
                onAddTransaction = {
                    ledger.prepare()
                    screen = AppScreen.LEDGER
                },
                onOpenSettings = { screen = AppScreen.SETTINGS },
                onOpenWatchlist = { screen = AppScreen.WATCHLIST },
                onPickerBusy = lock::setExternalUiActive,
            )
        AppScreen.LEDGER ->
            LedgerEntryRoute(
                viewModel = ledger,
                onClose = {
                    screen = AppScreen.DASHBOARD
                    dashboard.refresh()
                },
            )
        AppScreen.SETTINGS -> {
            val settings: TargetSettingsViewModel = viewModel(factory = TargetSettingsViewModel.factory(container))
            TargetSettingsRoute(
                targets = settings,
                lock = lock,
                importer = importer,
                onClose = {
                    screen = AppScreen.DASHBOARD
                    dashboard.refresh()
                },
                onPickerBusy = lock::setExternalUiActive,
                container = container,
            )
        }
        AppScreen.WATCHLIST -> {
            val watchlist: WatchlistViewModel = viewModel(factory = WatchlistViewModel.factory(container))
            WatchlistRoute(
                viewModel = watchlist,
                onClose = { screen = AppScreen.DASHBOARD },
            )
        }
    }
}

@Composable
private fun RequestNotificationPermission(onPickerBusy: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPickerBusy(false)
    }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            onPickerBusy(true)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
