package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.CurrencyRate
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "currency_history")
data class CurrencyRateEntity(@PrimaryKey val date: LocalDate, @ColumnInfo(name = "eur_usd_rate") val eurPerUsd: BigDecimal) {
    fun toDomain(): CurrencyRate = CurrencyRate(date, eurPerUsd)

    companion object {
        fun from(row: CurrencyRate) = CurrencyRateEntity(row.date, row.eurPerUsd)
    }
}
