package com.pirlruc.finsilo.domain.repository

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction

interface PortfolioReadRepository {
    suspend fun load(): PortfolioSnapshot
}

interface SamplePortfolioWriter {
    suspend fun write(snapshot: PortfolioSnapshot)
    suspend fun clear()
}

interface LedgerWriteRepository {
    suspend fun upsertAsset(asset: Asset)
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun replaceTargets(targets: List<TargetAllocation>)
    suspend fun upsertFxRate(rate: CurrencyRate)
}
