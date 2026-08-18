package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData
import java.math.BigDecimal
import java.time.LocalDate

@Entity(
    tableName = "daily_market_data",
    primaryKeys = ["asset_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("asset_id"), Index("date")],
)
data class DailyMarketDataEntity(
    @ColumnInfo(name = "asset_id") val assetId: String,
    val date: LocalDate,
    @ColumnInfo(name = "closing_price_native") val closingPriceNative: BigDecimal,
    @ColumnInfo(name = "analyst_rating") val analystRating: AnalystRating,
    @ColumnInfo(name = "sma_50") val sma50: BigDecimal?,
    @ColumnInfo(name = "sma_200") val sma200: BigDecimal?,
) {
    fun toDomain(): DailyMarketData = DailyMarketData(assetId, date, closingPriceNative, analystRating, sma50, sma200)

    companion object {
        fun from(row: DailyMarketData) = DailyMarketDataEntity(
            assetId = row.assetId,
            date = row.date,
            closingPriceNative = row.closingPriceNative,
            analystRating = row.analystRating,
            sma50 = row.sma50,
            sma200 = row.sma200,
        )
    }
}
