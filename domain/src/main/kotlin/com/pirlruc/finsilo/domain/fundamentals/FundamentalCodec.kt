package com.pirlruc.finsilo.domain.fundamentals

import java.math.BigDecimal
import java.time.LocalDate

/** Room payload for a valuation card. BigDecimal stays decimal text. */
object FundamentalCodec {
    fun encode(view: FundamentalView): String {
        val metrics = view.metrics
        return listOf(
            "v1",
            view.note.name,
            view.source,
            view.asOf?.toString().orEmpty(),
            field("pe", metrics.pe),
            field("peg", metrics.peg),
            field("fcf", metrics.fcfYield),
            field("roe", metrics.roe),
            field("roic", metrics.roic),
            field("de", metrics.debtToEquity),
            field("ev", metrics.evToEbit),
            field("cover", metrics.interestCoverage),
        ).joinToString("\n")
    }

    fun decode(raw: String): FundamentalView? {
        val lines = raw.lineSequence().toList()
        if (lines.size < 4 || lines[0] != "v1") return null
        val note = runCatching { ValuationNote.valueOf(lines[1]) }.getOrNull() ?: return null
        val asOf = lines[3].takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val values = lines.drop(4).mapNotNull { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1].toBigDecimalOrNull() else null
        }.toMap()
        return FundamentalView(
            note = note,
            source = lines[2],
            asOf = asOf,
            metrics = ValuationMetrics(
                pe = values["pe"],
                peg = values["peg"],
                fcfYield = values["fcf"],
                roe = values["roe"],
                roic = values["roic"],
                debtToEquity = values["de"],
                evToEbit = values["ev"],
                interestCoverage = values["cover"],
            ),
        )
    }

    private fun field(name: String, value: BigDecimal?): String = if (value == null) "" else "$name=${value.toPlainString()}"
}
