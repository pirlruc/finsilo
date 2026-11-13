package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Foreign tax withheld on a dividend. A missing or unreadable column stays unknown (null),
 * which is not the same as zero.
 */
internal object WithholdingTax {
    fun attach(line: BrokerCsvLine, row: CsvRow, trading212GrossNet: Boolean): BrokerCsvLine {
        val tax = stated(line, row, trading212GrossNet) ?: return line
        return line.copy(withholdingEur = tax)
    }

    private fun stated(line: BrokerCsvLine, row: CsvRow, trading212GrossNet: Boolean): BigDecimal? {
        if (line.type != TransactionType.DIVIDEND) return null
        if (row.indexOf(COLUMNS) != null) return fromColumns(row)
        if (!trading212GrossNet) return null
        return trading212Csv(row)
    }

    /** Gross per share times quantity, minus net, in EUR. Null when either side or the currency is missing. */
    fun grossMinusNet(grossPerShare: BigDecimal?, quantity: BigDecimal?, net: BigDecimal?, currency: String): BigDecimal? {
        if (!currency.equals("EUR", ignoreCase = true)) return null
        if (grossPerShare == null || quantity == null || net == null) return null
        val tax = grossPerShare.multiply(quantity).subtract(net)
        if (tax.signum() < 0) return null
        return tax
    }

    private fun fromColumns(row: CsvRow): BigDecimal? {
        val raw = row.get(*COLUMNS)
        if (raw.isBlank()) return null
        return BrokerMoney.absAmount(raw)
    }

    private fun trading212Csv(row: CsvRow): BigDecimal? {
        val currency = row.get("Currency (Total)").ifBlank { "EUR" }
        val shares = BrokerMoney.absAmount(row.get("No. of shares"))
        val price = BrokerMoney.absAmount(row.get("Price / share"))
        val total = BrokerMoney.absAmount(row.get("Total"))
        return grossMinusNet(price, shares, total, currency)
    }

    private val COLUMNS = arrayOf(
        "Withholding tax",
        "Dividend tax",
        "Tax withheld",
        "Imposto retido",
        "Retencao na fonte",
        "Quellensteuer",
        "Bronbelasting",
        "Withholding",
    )
}
