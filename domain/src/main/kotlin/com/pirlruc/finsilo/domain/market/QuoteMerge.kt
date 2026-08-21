package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData

/** Replaces snapshot quote/FX rows with a later sync without another full load. */
object QuoteMerge {
    fun market(old: List<DailyMarketData>, incoming: List<DailyMarketData>): List<DailyMarketData> {
        if (incoming.isEmpty()) return old
        val next = incoming.associateBy { it.assetId to it.date }
        return old.filter { (it.assetId to it.date) !in next } + incoming
    }

    fun fx(old: List<CurrencyRate>, incoming: List<CurrencyRate>): List<CurrencyRate> {
        if (incoming.isEmpty()) return old
        val next = incoming.associateBy { it.date }
        return old.filter { it.date !in next } + incoming
    }

    fun plusLatest(ranged: List<DailyMarketData>, latest: List<DailyMarketData>): List<DailyMarketData> {
        val have = ranged.map { it.assetId }.toHashSet()
        return ranged + latest.filter { it.assetId !in have }
    }
}
