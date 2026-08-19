package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.NavPoint
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "nav_history")
data class NavHistoryEntity(@PrimaryKey val date: LocalDate, @ColumnInfo(name = "value_eur") val valueEur: BigDecimal) {
    fun toDomain() = NavPoint(date, valueEur)

    companion object {
        fun from(point: NavPoint) = NavHistoryEntity(point.date, point.valueEur)
    }
}
