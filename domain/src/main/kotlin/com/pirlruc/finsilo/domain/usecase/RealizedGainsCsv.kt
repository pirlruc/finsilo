package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.RealizedGainsReport
import com.pirlruc.finsilo.domain.model.RealizedKind
import java.math.RoundingMode

/** Portuguese plus-valias CSV from a [RealizedGainsReport]. Figures match the domain report. */
object RealizedGainsCsv {
    const val HEADER: String =
        "Ativo;ISIN;Tipo;Data venda;Data aquisicao;Quantidade;Custo EUR;Produto EUR;Mais-valia EUR"

    /** Semicolon CSV so Portuguese decimal commas in formatted numbers stay unambiguous. */
    fun write(report: RealizedGainsReport): String {
        val rows = ArrayList<String>(report.lines.size + 2)
        rows += HEADER
        for (line in report.lines) {
            rows +=
                listOf(
                    csv(line.asset.symbol),
                    csv(line.asset.isin.orEmpty()),
                    csv(kindLabel(line.kind)),
                    csv(line.sellDate.toString()),
                    csv(line.acquiredDate.toString()),
                    csv(plain(line.quantity)),
                    csv(plain(line.costEur)),
                    csv(plain(line.proceedsEur)),
                    csv(plain(line.gainEur)),
                ).joinToString(";")
        }
        rows += "Total;;;;;;;;${plain(report.totalGainEur)}"
        return rows.joinToString("\n")
    }

    private fun kindLabel(kind: RealizedKind): String = when (kind) {
        RealizedKind.DISPOSAL -> "Alienacao"
        RealizedKind.REDEMPTION -> "Resgate"
    }

    private fun plain(value: java.math.BigDecimal): String = value.setScale(2, RoundingMode.HALF_EVEN).toPlainString()

    private fun csv(value: String): String {
        if (value.none { it == ';' || it == '"' || it == '\n' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
