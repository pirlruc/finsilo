package com.pirlruc.finsilo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nav_rebuild_state")
data class NavRebuildStateEntity(@PrimaryKey val id: Int = 1, val fingerprint: String)
