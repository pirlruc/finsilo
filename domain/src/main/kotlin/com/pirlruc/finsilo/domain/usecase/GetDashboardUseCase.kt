package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate

class GetDashboardUseCase {
    operator fun invoke(snapshot: PortfolioSnapshot, range: HistoryRange, asOf: LocalDate): DashboardReport {
        val valuator = PortfolioValuator()
        val warnings =
            if (valuator.missingUsdFx(snapshot)) {
                listOf("USD holdings need an FX quote before they can be valued.")
            } else {
                emptyList()
            }
        return DashboardReport(
            asOf = asOf,
            allocation = GetAllocationUseCase(valuator)(snapshot, asOf),
            history = GetPortfolioHistoryUseCase(valuator)(snapshot, range, asOf),
            signals = GetMarketSignalsUseCase()(snapshot, asOf),
            twr = GetTimeWeightedReturnUseCase(valuator)(snapshot, asOf),
            yoc = GetYocUseCase()(snapshot, asOf),
            warnings = warnings,
        )
    }
}
