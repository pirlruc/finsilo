package com.pirlruc.finsilo.domain.importcsv

/** Header + data rows with alias lookup. */
internal data class CsvTable(val headers: List<String>, val rows: List<List<String>>) {
    fun forEachRow(block: (lineNumber: Int, row: CsvRow) -> Unit) {
        rows.forEachIndexed { index, cells ->
            if (cells.any { it.isNotBlank() }) {
                block(index + 2, CsvRow(headers, cells))
            }
        }
    }
}

/** One CSV data row. */
internal class CsvRow(private val headers: List<String>, private val cells: List<String>) {
    fun get(vararg aliases: String): String {
        exact(aliases)?.let { return it }
        return prefix(aliases).orEmpty()
    }

    private fun exact(aliases: Array<out String>): String? {
        for (alias in aliases) {
            val want = normalizeHeader(alias)
            val index = headers.indexOfFirst { normalizeHeader(it) == want }
            if (index >= 0) return cellAt(index)
        }
        return null
    }

    private fun prefix(aliases: Array<out String>): String? {
        for (alias in aliases) {
            val want = normalizeHeader(alias)
            val index = headers.indexOfFirst { normalizeHeader(it).startsWith(want) }
            if (index >= 0) {
                val value = cellAt(index)
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun cellAt(index: Int): String = cells.getOrNull(index)?.trim().orEmpty()
}

internal fun normalizeHeader(raw: String): String = raw.trim().trim('\uFEFF').lowercase().replace(Regex("\\s+"), " ")
