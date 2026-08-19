package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.WatchlistItem

@Entity(tableName = "watchlist_item")
data class WatchlistItemEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    val symbol: String,
    val name: String,
    @ColumnInfo(name = "asset_type") val assetType: AssetType,
    @ColumnInfo(name = "base_currency") val baseCurrency: Currency,
    @ColumnInfo(name = "quote_symbol") val quoteSymbol: String? = null,
) {
    fun toDomain() = WatchlistItem(
        itemId,
        symbol,
        name,
        assetType,
        baseCurrency,
        quoteSymbol,
    )

    companion object {
        fun from(row: WatchlistItem) = WatchlistItemEntity(row.id, row.symbol, row.name, row.assetType, row.baseCurrency, row.quoteSymbol)
    }
}
