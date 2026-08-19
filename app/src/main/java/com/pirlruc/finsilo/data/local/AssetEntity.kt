package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") val assetId: String,
    val symbol: String,
    val name: String,
    @ColumnInfo(name = "asset_type") val assetType: AssetType,
    @ColumnInfo(name = "base_currency") val baseCurrency: Currency,
    val isin: String? = null,
    @ColumnInfo(name = "quote_symbol") val quoteSymbol: String? = null,
) {
    fun toDomain(): Asset = Asset(assetId, symbol, name, assetType, baseCurrency, isin, quoteSymbol)

    companion object {
        fun from(asset: Asset) = AssetEntity(
            asset.id,
            asset.symbol,
            asset.name,
            asset.assetType,
            asset.baseCurrency,
            asset.isin,
            asset.quoteSymbol,
        )
    }
}
