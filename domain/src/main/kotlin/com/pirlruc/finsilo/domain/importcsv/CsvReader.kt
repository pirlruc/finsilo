package com.pirlruc.finsilo.domain.importcsv

/**
 * RFC-4180-ish CSV splitter. Detects comma vs semicolon from the first
 * non-empty line so DEGIRO (semicolon) and T212/Revolut (comma) share one reader.
 */
object CsvReader {
    /** Split [text] into rows of cells, stripping a leading BOM. */
    fun records(text: String): List<List<String>> {
        val body = text.removePrefix("\uFEFF")
        if (body.isBlank()) return emptyList()
        return CsvLexer(body, detectDelimiter(body)).read()
    }

    internal fun detectDelimiter(text: String): Char {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val semi = line.count { it == ';' }
        val comma = line.count { it == ',' }
        return if (semi > comma) ';' else ','
    }
}

private class CsvLexer(private val text: String, private val delimiter: Char) {
    private val rows = ArrayList<List<String>>()
    private val cell = StringBuilder()
    private val row = ArrayList<String>()
    private var quoted = false
    private var i = 0

    fun read(): List<List<String>> {
        while (i < text.length) {
            consume(text[i])
        }
        finishCell()
        if (row.isNotEmpty()) rows += row.toList()
        return rows
    }

    private fun consume(ch: Char) {
        when {
            quoted && ch == '"' -> consumeQuote()
            quoted -> appendAndAdvance(ch)
            ch == '"' -> {
                quoted = true
                i += 1
            }
            ch == delimiter -> finishCell()
            ch == '\n' -> finishRow()
            ch == '\r' -> i += 1
            else -> appendAndAdvance(ch)
        }
    }

    private fun consumeQuote() {
        val doubled = i + 1 < text.length && text[i + 1] == '"'
        if (doubled) {
            cell.append('"')
            i += 2
        } else {
            quoted = false
            i += 1
        }
    }

    private fun appendAndAdvance(ch: Char) {
        cell.append(ch)
        i += 1
    }

    private fun finishCell() {
        row += cell.toString()
        cell.setLength(0)
        i += 1
    }

    private fun finishRow() {
        finishCell()
        rows += row.toList()
        row.clear()
    }
}
