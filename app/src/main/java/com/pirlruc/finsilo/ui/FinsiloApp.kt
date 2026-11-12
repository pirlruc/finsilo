package com.pirlruc.finsilo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.ui.dashboard.DashboardRoute
import com.pirlruc.finsilo.ui.dashboard.DashboardUiState
import com.pirlruc.finsilo.ui.dashboard.DashboardViewModel
import com.pirlruc.finsilo.ui.dashboard.HomeSection
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel
import com.pirlruc.finsilo.ui.ledger.ActivityLedger
import com.pirlruc.finsilo.ui.ledger.LedgerEntryRoute
import com.pirlruc.finsilo.ui.ledger.LedgerEntryViewModel
import com.pirlruc.finsilo.ui.lock.AppLockGate
import com.pirlruc.finsilo.ui.lock.LockViewModel
import com.pirlruc.finsilo.ui.settings.TargetSettingsRoute
import com.pirlruc.finsilo.ui.settings.TargetSettingsViewModel
import com.pirlruc.finsilo.ui.watchlist.WatchlistRoute
import com.pirlruc.finsilo.ui.watchlist.WatchlistViewModel

private enum class AppScreen {
    HOME,
    LEDGER,
    SETTINGS,
    WATCHLIST,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Text(tab.name.take(1)) },
                label = { Text(tab.name.lowercase().replaceFirstChar { it.titlecase() }) },
            )
        }
    }
}

@Composable
fun FinsiloApp(container: AppContainer) {
    val lock: LockViewModel = viewModel(factory = LockViewModel.factory(container))
    AppLockGate(lock) {
        RequestNotificationPermission()
        UnlockedApp(container, lock)
    }
}

@Composable
private fun UnlockedApp(container: AppContainer, lock: LockViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.OVERVIEW) }
    val dashboard: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val importer: BrokerImportViewModel = viewModel(factory = BrokerImportViewModel.factory(container))
    val ledger: LedgerEntryViewModel = viewModel(factory = LedgerEntryViewModel.factory(container))
    val onPickerBusy = rememberPickerBusy(lock)
    val dashState by dashboard.state.collectAsStateWithLifecycle()
    BackHandler(enabled = screen != AppScreen.HOME) {
        screen = AppScreen.HOME
        dashboard.refresh()
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (screen == AppScreen.HOME) {
                MainNavigationBar(tab) { tab = it }
            }
        },
    ) { padding ->
        when (screen) {
            AppScreen.HOME ->
                HomeTabPage(
                    tab = tab,
                    padding = padding,
                    dashboard = dashboard,
                    dashState = dashState,
                    importer = importer,
                    ledger = ledger,
                    onPickerBusy = onPickerBusy,
                    onScreen = { screen = it },
                )
            AppScreen.LEDGER ->
                LedgerEntryRoute(
                    viewModel = ledger,
                    onClose = {
                        screen = AppScreen.HOME
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
                        screen = AppScreen.HOME
                        dashboard.refresh()
                    },
                    onPickerBusy = onPickerBusy,
                    container = container,
                )
            }
            AppScreen.WATCHLIST -> {
                val watchlist: WatchlistViewModel = viewModel(factory = WatchlistViewModel.factory(container))
                WatchlistRoute(
                    viewModel = watchlist,
                    onClose = { screen = AppScreen.HOME },
                )
            }
        }
    }
}

@Composable
private fun HomeTabPage(
    tab: HomeTab,
    padding: PaddingValues,
    dashboard: DashboardViewModel,
    dashState: DashboardUiState,
    importer: BrokerImportViewModel,
    ledger: LedgerEntryViewModel,
    onPickerBusy: (Boolean) -> Unit,
    onScreen: (AppScreen) -> Unit,
) {
    Column(Modifier.padding(padding).imePadding()) {
        if (tab == HomeTab.ACTIVITY) {
            ActivityLedger(
                transactions = dashState.transactions,
                assets = dashState.assets,
                onAdd = {
                    ledger.prepare()
                    onScreen(AppScreen.LEDGER)
                },
                onEdit = { tx ->
                    ledger.edit(tx)
                    onScreen(AppScreen.LEDGER)
                },
                onDelete = dashboard::deleteTransaction,
            )
        } else {
            DashboardRoute(
                viewModel = dashboard,
                importer = importer,
                onAddTransaction = {
                    ledger.prepare()
                    onScreen(AppScreen.LEDGER)
                },
                onOpenSettings = { onScreen(AppScreen.SETTINGS) },
                onOpenWatchlist = { onScreen(AppScreen.WATCHLIST) },
                onPickerBusy = onPickerBusy,
                section = homeSection(tab),
            )
        }
    }
}

private fun homeSection(tab: HomeTab): HomeSection = when (tab) {
    HomeTab.HOLDINGS -> HomeSection.HOLDINGS
    HomeTab.INSIGHTS -> HomeSection.INSIGHTS
    else -> HomeSection.OVERVIEW
}

@Composable
private fun rememberPickerBusy(lock: LockViewModel): (Boolean) -> Unit {
    val activity = LocalActivity.current
    val state by lock.state.collectAsStateWithLifecycle()
    val covering = state.externalUiActive
    LaunchedEffect(covering, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (covering) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    return remember(lock, activity) {
        { busy ->
            if (!busy) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            lock.setExternalUiActive(busy)
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
