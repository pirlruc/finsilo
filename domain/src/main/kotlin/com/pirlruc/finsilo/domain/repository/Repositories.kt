package com.pirlruc.finsilo.domain.repository

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot

interface PortfolioReadRepository {
    suspend fun load(): PortfolioSnapshot
}

interface SamplePortfolioWriter {
    suspend fun write(snapshot: PortfolioSnapshot)
    suspend fun clear()
}
