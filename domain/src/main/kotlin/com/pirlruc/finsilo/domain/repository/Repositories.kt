package com.pirlruc.finsilo.domain.repository

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction

/** Loads the on-device portfolio snapshot. */
interface PortfolioReadRepository {
    suspend fun load(): PortfolioSnapshot
}

/** Writes or clears the sample portfolio. */
interface SamplePortfolioWriter {
    suspend fun write(snapshot: PortfolioSnapshot)

    suspend fun clear()
}

/** Ledger mutations used by the entry form and settings. */
interface LedgerWriteRepository {
    suspend fun upsertAsset(asset: Asset)

    suspend fun insertTransaction(transaction: Transaction)

    suspend fun replaceTargets(targets: List<TargetAllocation>)

    suspend fun upsertFxRate(rate: CurrencyRate)

    /** Persist a new instrument, its first (or next) row, and optional FX seed atomically. */
    suspend fun saveLedgerEntry(asset: Asset?, transaction: Transaction, fxRate: CurrencyRate?)
}
