package com.pirlruc.finsilo.ui.dashboard

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

internal fun dashboardViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        createDashboardViewModel(container)
    }
}
