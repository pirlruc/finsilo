package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.TargetAllocation
import java.math.BigDecimal

@Entity(tableName = "target_allocation")
data class TargetAllocationEntity(
    @PrimaryKey @ColumnInfo(name = "asset_type") val assetType: AssetType,
    @ColumnInfo(name = "weight_percent") val weightPercent: BigDecimal,
) {
    fun toDomain(): TargetAllocation = TargetAllocation(assetType, weightPercent)

    companion object {
        fun from(row: TargetAllocation) = TargetAllocationEntity(row.assetType, row.weightPercent)
    }
}
