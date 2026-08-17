package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate

/**
 * Allocation and history share one [PortfolioValuator] (date cache bound to this
 * snapshot). TWR values partial ledgers, so it uses its own valuator.
 */
class GetDashboardUseCase {
    operator fun invoke(
        snapshot: PortfolioSnapshot,
        range: HistoryRange,
        asOf: LocalDate,
        storedNav: List<NavPoint> = emptyList(),
    ): DashboardReport {
        val valuator = PortfolioValuator()
        val warnings = valuator.valuationWarnings(snapshot, asOf)
        return DashboardReport(
            asOf = asOf,
            allocation = GetAllocationUseCase(valuator)(snapshot, asOf),
            history = GetPortfolioHistoryUseCase(valuator)(snapshot, range, asOf, storedNav),
            signals = GetMarketSignalsUseCase()(snapshot, asOf),
            twr = GetTimeWeightedReturnUseCase()(snapshot, asOf),
            yoc = GetYocUseCase()(snapshot, asOf),
            warnings = warnings,
        )
    }
}
