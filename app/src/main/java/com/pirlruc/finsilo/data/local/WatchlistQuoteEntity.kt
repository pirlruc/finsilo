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
    tableName = "watchlist_quote",
    primaryKeys = ["item_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = WatchlistItemEntity::class,
            parentColumns = ["item_id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_id")],
)
data class WatchlistQuoteEntity(
    @ColumnInfo(name = "item_id") val itemId: String,
    val date: LocalDate,
    @ColumnInfo(name = "closing_price_native") val closingPriceNative: BigDecimal,
    @ColumnInfo(name = "analyst_rating") val analystRating: AnalystRating,
    @ColumnInfo(name = "sma_50") val sma50: BigDecimal?,
    @ColumnInfo(name = "sma_200") val sma200: BigDecimal?,
) {
    fun toDomain() = DailyMarketData(itemId, date, closingPriceNative, analystRating, sma50, sma200)

    companion object {
        fun from(row: DailyMarketData) =
            WatchlistQuoteEntity(row.assetId, row.date, row.closingPriceNative, row.analystRating, row.sma50, row.sma200)
    }
}
