package com.pirlruc.finsilo.domain.performance

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.RealizedLotLine
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.usecase.GetRealizedGainsUseCase
import java.math.BigDecimal

/** Portuguese IRS quadros this report can fill. It is not the official XML. */
enum class IrsQuadro {
    ANEXO_J_8A,
    ANEXO_J_9_2A,
    ANEXO_G_9,
}

/** One annual row for Anexo J quadro 8A or 9.2A, or Anexo G quadro 9. */
data class IrsAnnualRow(
    val quadro: IrsQuadro,
    val countryCode: String,
    val grossIncomeEur: BigDecimal,
    /** Null when the broker did not state a withholding amount. That is not zero. */
    val foreignTaxWithheldEur: BigDecimal?,
    val acquisitionValueEur: BigDecimal,
    val realizationValueEur: BigDecimal,
    val year: Int,
)

/** Country of source from the listing suffix, else the ISIN prefix. Anything else is unknown. */
object ListingCountry {
    fun code(symbol: String, isin: String? = null): String {
        val suffix = symbol.substringAfterLast('.', missingDelimiterValue = "").uppercase()
        if (suffix.isNotEmpty()) return SUFFIX[suffix] ?: UNKNOWN
        return isinPrefix(isin)
    }

    private fun isinPrefix(isin: String?): String {
        val prefix = isin?.take(2)?.uppercase().orEmpty()
        if (prefix.length != 2 || prefix.any { !it.isLetter() }) return UNKNOWN
        return prefix
    }

    const val UNKNOWN: String = "unknown"

    private val SUFFIX = mapOf(
        "DE" to "DE",
        "PA" to "FR",
        "AS" to "NL",
        "MI" to "IT",
        "MC" to "ES",
        "L" to "GB",
        "LON" to "GB",
        "UK" to "GB",
        "SW" to "CH",
        "VX" to "CH",
        "LS" to "PT",
        "US" to "US",
        "CO" to "DK",
        "ST" to "SE",
        "OL" to "NO",
        "HE" to "FI",
        "BR" to "BE",
        "IR" to "IE",
        "VI" to "AT",
    )
}

/**
 * Builds Anexo J 8A / 9.2A and Anexo G 9 rows from FIFO realized gains and dividends.
 * This is not tax advice and it is not the official IRS XML.
 */
class IrsAnnualReport(private val gains: GetRealizedGainsUseCase = GetRealizedGainsUseCase()) {
    fun rows(
        snapshot: PortfolioSnapshot,
        year: Int,
        countryOf: (Asset) -> String = { ListingCountry.code(it.feedSymbol, it.isin) },
    ): List<IrsAnnualRow> {
        val assets = snapshot.assets.associateBy { it.id }
        val income = snapshot.transactions.mapNotNull { dividendRow(it, assets[it.assetId], year, countryOf) }
        val disposals = gains(snapshot, year).lines.map { gainRow(it, year, countryOf) }
        return income + disposals
    }

    private fun dividendRow(tx: Transaction, asset: Asset?, year: Int, countryOf: (Asset) -> String): IrsAnnualRow? {
        if (tx.type != TransactionType.DIVIDEND || tx.date.year != year || asset == null) return null
        return IrsAnnualRow(
            quadro = IrsQuadro.ANEXO_J_8A,
            countryCode = countryOf(asset),
            grossIncomeEur = tx.notionalEur,
            foreignTaxWithheldEur = tx.withholdingEur,
            acquisitionValueEur = BigDecimal.ZERO,
            realizationValueEur = BigDecimal.ZERO,
            year = year,
        )
    }

    private fun gainRow(line: RealizedLotLine, year: Int, countryOf: (Asset) -> String): IrsAnnualRow {
        val country = countryOf(line.asset)
        val quadro = if (country == "PT") IrsQuadro.ANEXO_G_9 else IrsQuadro.ANEXO_J_9_2A
        return IrsAnnualRow(
            quadro = quadro,
            countryCode = country,
            grossIncomeEur = line.gainEur,
            foreignTaxWithheldEur = null,
            acquisitionValueEur = line.costEur,
            realizationValueEur = line.proceedsEur,
            year = year,
        )
    }
}

/** CSV for [IrsAnnualRow]. The PDF writer lives in the app module. */
object IrsAnnualCsv {
    fun write(rows: List<IrsAnnualRow>): String {
        val header = "quadro,country,gross_income_eur,foreign_tax_withheld_eur,acquisition_value_eur,realization_value_eur,year"
        val body = rows.joinToString("\n") { row ->
            listOf(
                row.quadro.name,
                row.countryCode,
                row.grossIncomeEur.toPlainString(),
                row.foreignTaxWithheldEur?.toPlainString() ?: "unknown",
                row.acquisitionValueEur.toPlainString(),
                row.realizationValueEur.toPlainString(),
                row.year.toString(),
            ).joinToString(",")
        }
        return if (body.isEmpty()) header else "$header\n$body"
    }
}
