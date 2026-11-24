package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fundamental_cache")
data class FundamentalCacheEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") val assetId: String,
    val payload: String,
    @ColumnInfo(name = "fetched_epoch_day") val fetchedEpochDay: Long,
)
