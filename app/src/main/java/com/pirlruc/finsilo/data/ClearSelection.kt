package com.pirlruc.finsilo.data

/** Which on-device stores a clear action should wipe. */
data class ClearSelection(val ledger: Boolean = true, val watchlist: Boolean = true, val templates: Boolean = true) {
    /** True when at least one store is selected. */
    val any: Boolean get() = ledger || watchlist || templates
}
