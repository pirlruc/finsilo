package com.pirlruc.finsilo.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.sync.PortfolioAlertNotifier

internal fun createDashboardViewModel(container: AppContainer): DashboardViewModel {
    val notifier = PortfolioAlertNotifier(container.application)
    return DashboardViewModel(
        container.repository,
        container.getDashboard,
        container,
        notify = { notifier.publish(it) },
    )
}

internal fun dashboardViewModelFactory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = createDashboardViewModel(container) as T
}
