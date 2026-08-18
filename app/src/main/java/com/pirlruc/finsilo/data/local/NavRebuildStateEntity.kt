package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "nav_rebuild_state")
data class NavRebuildStateEntity(
    @PrimaryKey val id: Int = 1,
    val fingerprint: String,
    @ColumnInfo(name = "as_of") val asOf: LocalDate,
    @ColumnInfo(name = "rebuilt_at_ms") val rebuiltAtMs: Long,
)
