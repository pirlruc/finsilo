package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.time.LocalDate

class GetDashboardUseCase(
    private val allocation: GetAllocationUseCase = GetAllocationUseCase(),
    private val history: GetPortfolioHistoryUseCase = GetPortfolioHistoryUseCase(),
    private val signals: GetMarketSignalsUseCase = GetMarketSignalsUseCase(),
    private val twr: GetTimeWeightedReturnUseCase = GetTimeWeightedReturnUseCase(),
    private val yoc: GetYocUseCase = GetYocUseCase(),
) {
    operator fun invoke(
        snapshot: PortfolioSnapshot,
        range: HistoryRange,
        asOf: LocalDate,
    ): DashboardReport =
        DashboardReport(
            asOf = asOf,
            allocation = allocation(snapshot, asOf),
            history = history(snapshot, range, asOf),
            signals = signals(snapshot, asOf),
            twr = twr(snapshot, asOf),
            yoc = yoc(snapshot, asOf),
        )
}
