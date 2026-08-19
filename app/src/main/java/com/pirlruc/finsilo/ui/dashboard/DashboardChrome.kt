package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardTopBar(
    empty: Boolean,
    syncing: Boolean,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onShowKey: () -> Unit,
    onSync: () -> Unit,
    onRequestClear: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_silo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text("FinSilo")
            }
        },
        actions = {
            IconButton(onClick = onOpenWatchlist) {
                Icon(Icons.Outlined.Star, contentDescription = "Watchlist")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
            if (!empty) {
                IconButton(onClick = onSync, enabled = !syncing) {
                    Icon(Icons.Outlined.Sync, contentDescription = "Sync quotes")
                }
            }
            DashboardOverflowMenu(onShowKey = onShowKey, onRequestClear = onRequestClear)
        },
    )
}

@Composable
private fun DashboardOverflowMenu(onShowKey: () -> Unit, onRequestClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Alpha Vantage key") },
            onClick = {
                expanded = false
                onShowKey()
            },
            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Clear data") },
            onClick = {
                expanded = false
                onRequestClear()
            },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        )
    }
}
