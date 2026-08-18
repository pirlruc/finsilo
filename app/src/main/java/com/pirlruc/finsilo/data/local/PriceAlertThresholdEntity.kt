package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import java.math.BigDecimal

@Entity(
    tableName = "price_alert_threshold",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PriceAlertThresholdEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") val assetId: String,
    @ColumnInfo(name = "eur_level") val eurLevel: BigDecimal? = null,
    @ColumnInfo(name = "percent_move") val percentMove: BigDecimal? = null,
) {
    fun toDomain() = PriceAlertThreshold(assetId, eurLevel, percentMove)

    companion object {
        fun from(row: PriceAlertThreshold) = PriceAlertThresholdEntity(row.assetId, row.eurLevel, row.percentMove)
    }
}
