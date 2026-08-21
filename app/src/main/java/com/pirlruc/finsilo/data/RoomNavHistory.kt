package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.NavHistoryEntity
import com.pirlruc.finsilo.data.local.NavRebuildStateEntity
import com.pirlruc.finsilo.data.local.PortfolioDao
import com.pirlruc.finsilo.data.sync.WidgetNavCache
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.usecase.RebuildNavHistoryUseCase
import java.time.LocalDate

internal object RoomNavHistory {
    private val rebuildNav = RebuildNavHistoryUseCase()

    suspend fun load(dao: PortfolioDao): List<NavPoint> = dao.getNavHistory().map { it.toDomain() }

    suspend fun lastPoint(dao: PortfolioDao): NavPoint? = dao.getNavHistory().maxByOrNull { it.date }?.toDomain()

    suspend fun rebuildIfNeeded(
        dao: PortfolioDao,
        widgetNav: WidgetNavCache?,
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
        changedFrom: LocalDate?,
    ) {
        if (snapshot.isEmpty) return
        val stored = dao.getNavHistory().map { it.toDomain() }
        val decision = rebuildNav(snapshot, asOf, dao.getNavRebuildState()?.fingerprint, stored, changedFrom)
        if (decision.skip) {
            widgetNav?.write(decision.points.maxByOrNull { it.date })
            return
        }
        dao.replaceNavHistory(
            items = decision.points.map(NavHistoryEntity::from),
            state = NavRebuildStateEntity(
                fingerprint = decision.fingerprint,
                asOf = asOf,
                rebuiltAtMs = System.currentTimeMillis(),
            ),
        )
        widgetNav?.write(decision.points.maxByOrNull { it.date })
    }
}
