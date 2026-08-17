package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

internal data class HoldingDraft(
    val format: BrokerCsvFormat,
    val sourceLine: Int,
    val date: LocalDate,
    val type: TransactionType,
    val symbol: String,
    val name: String,
    val isin: String?,
    val quoteSymbol: String?,
    val booked: BookedAmounts,
    val externalId: String?,
)

internal object BrokerLines {
    fun skip(format: BrokerCsvFormat, sourceLine: Int, reason: String, date: LocalDate? = null): BrokerCsvLine = BrokerCsvLine(
        date = date,
        type = null,
        skipReason = reason,
        symbol = "",
        name = "",
        isin = null,
        quoteSymbol = null,
        assetType = AssetType.STOCK,
        quantity = BigDecimal.ZERO,
        unitPriceNative = BigDecimal.ZERO,
        currency = Currency.EUR,
        feesEur = BigDecimal.ZERO,
        eurPerUsd = null,
        externalId = null,
        sourceLine = sourceLine,
        format = format,
    )

    fun cash(
        format: BrokerCsvFormat,
        sourceLine: Int,
        date: LocalDate,
        type: TransactionType,
        amountEur: BigDecimal,
        externalId: String?,
    ): BrokerCsvLine = BrokerCsvLine(
        date = date,
        type = type,
        skipReason = null,
        symbol = "EUR-CASH",
        name = "Euro cash",
        isin = null,
        quoteSymbol = null,
        assetType = AssetType.CASH,
        quantity = amountEur.abs(),
        unitPriceNative = BigDecimal.ONE,
        currency = Currency.EUR,
        feesEur = BigDecimal.ZERO,
        eurPerUsd = null,
        externalId = externalId,
        sourceLine = sourceLine,
        format = format,
    )

    fun holding(draft: HoldingDraft): BrokerCsvLine = BrokerCsvLine(
        date = draft.date,
        type = draft.type,
        skipReason = null,
        symbol = draft.symbol,
        name = draft.name.ifBlank { draft.symbol },
        isin = draft.isin?.ifBlank { null },
        quoteSymbol = draft.quoteSymbol?.ifBlank { null },
        assetType = BrokerAssetType.infer(draft.symbol, draft.name, draft.isin),
        quantity = draft.booked.quantity,
        unitPriceNative = draft.booked.unitPriceNative,
        currency = draft.booked.currency,
        feesEur = draft.booked.feesEur,
        eurPerUsd = draft.booked.eurPerUsd,
        externalId = draft.externalId?.ifBlank { null },
        sourceLine = draft.sourceLine,
        format = draft.format,
    )
}
