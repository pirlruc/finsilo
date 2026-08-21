package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope

@Entity(tableName = "rating_alert", primaryKeys = ["target_id", "scope"])
data class RatingAlertEntity(@ColumnInfo(name = "target_id") val targetId: String, val scope: String, val levels: Int) {
    fun toDomain() = RatingAlertPref.fromMask(targetId, RatingAlertScope.valueOf(scope), levels)

    companion object {
        fun from(row: RatingAlertPref) = RatingAlertEntity(row.targetId, row.scope.name, row.mask)
    }
}
