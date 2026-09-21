package com.pirlruc.finsilo.domain.importcsv

import java.text.Normalizer

/** Header + data rows with alias lookup. */
internal data class CsvTable(val headers: List<String>, val rows: List<List<String>>) {
    fun mapRows(block: (lineNumber: Int, row: CsvRow) -> BrokerCsvLine): List<BrokerCsvLine> {
        val lines = ArrayList<BrokerCsvLine>()
        rows.forEachIndexed { index, cells ->
            if (cells.any { it.isNotBlank() }) {
                lines += block(index + 2, CsvRow(headers, cells))
            }
        }
        return lines
    }
}

/** One CSV data row. */
internal class CsvRow(private val headers: List<String>, private val cells: List<String>) {
    fun get(vararg aliases: String): String {
        exact(aliases)?.let { return it }
        return prefix(aliases).orEmpty()
    }

    fun getAny(aliases: Set<String>): String = get(*aliases.toTypedArray())

    fun pairedAny(aliases: Set<String>): Pair<String, String> = pairedAmount(*aliases.toTypedArray())

    /** Currency + amount when DEGIRO pairs a named header with an empty following column. */
    fun pairedAmount(vararg aliases: String): Pair<String, String> {
        val index = indexOf(aliases) ?: return "" to get(*aliases)
        val first = cellAt(index)
        val second = cellAt(index + 1)
        return when {
            looksCurrencyCode(first) && second.isNotEmpty() -> first to second
            looksCurrencyCode(second) && first.isNotEmpty() -> second to first
            else -> "" to first
        }
    }

    fun indexOf(aliases: Array<out String>): Int? = firstHeader(aliases) { header, want -> header == want }
        ?: firstHeader(aliases) { header, want -> header.startsWith(want) }

    private fun exact(aliases: Array<out String>): String? = firstHeader(aliases) { header, want -> header == want }?.let { cellAt(it) }

    private fun prefix(aliases: Array<out String>): String? {
        for (alias in aliases) {
            val index = firstHeader(arrayOf(alias)) { header, want -> header.startsWith(want) }
            if (index != null && cellAt(index).isNotEmpty()) return cellAt(index)
        }
        return null
    }

    private fun firstHeader(aliases: Array<out String>, match: (String, String) -> Boolean): Int? {
        for (alias in aliases) {
            val want = normalizeHeader(alias)
            val index = headers.indexOfFirst { match(normalizeHeader(it), want) }
            if (index >= 0) return index
        }
        return null
    }

    private fun cellAt(index: Int): String = cells.getOrNull(index)?.trim().orEmpty()
}

internal fun normalizeHeader(raw: String): String {
    val folded = Normalizer.normalize(raw.trim().trim('\uFEFF').lowercase(), Normalizer.Form.NFD)
    return folded.replace(DIACRITICS, "").replace(Regex("\\s+"), " ")
}

internal fun looksCurrencyCode(raw: String): Boolean = raw.trim().filter { it.isLetter() }.length == 3

private val DIACRITICS = Regex("\\p{M}+")
