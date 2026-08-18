package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.usecase.LedgerEntryRequest
import com.pirlruc.finsilo.domain.usecase.LedgerEntryResult
import com.pirlruc.finsilo.domain.usecase.NewAssetDraft
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import java.math.BigDecimal

/** Counts and the resulting snapshot after a broker CSV import. */
data class ImportBrokerCsvResult(
    val accepted: Int,
    val fundedDeposits: Int,
    val duplicates: Int,
    val skipped: List<String>,
    val snapshot: PortfolioSnapshot,
    val error: String? = null,
)

/**
 * Parses Trading 212 / DEGIRO / Revolut CSVs and writes FIFO ledger rows.
 * Buys that need cash insert a same-day [TransactionType.DEPOSIT_CASH] first.
 */
class ImportBrokerCsvUseCase internal constructor(private val applyLedger: (PortfolioSnapshot, LedgerEntryRequest) -> LedgerEntryResult) {
    constructor(record: RecordLedgerEntryUseCase = RecordLedgerEntryUseCase()) : this(
        { snapshot, request -> record(snapshot, request) },
    )

    private val funder = ImportCashFunder()

    operator fun invoke(snapshot: PortfolioSnapshot, csvTexts: List<String>): ImportBrokerCsvResult {
        val parsed = BrokerCsv.parseAll(csvTexts)
        if (parsed.error != null) {
            return ImportBrokerCsvResult(0, 0, 0, emptyList(), snapshot, parsed.error)
        }
        return ImportWalk(snapshot, applyLedger, funder).apply(parsed.lines)
    }
}

private class ImportWalk(
    initial: PortfolioSnapshot,
    private val applyLedger: (PortfolioSnapshot, LedgerEntryRequest) -> LedgerEntryResult,
    private val funder: ImportCashFunder,
) {
    private var snapshot = initial
    private var accepted = 0
    private var fundedDeposits = 0
    private var duplicates = 0
    private val skipped = ArrayList<String>()
    private val already = ImportFingerprints.counts(initial)
    private val seen = HashMap<String, Int>()

    fun apply(lines: List<BrokerCsvLine>): ImportBrokerCsvResult {
        ordered(lines).forEach { line -> handle(line) }
        return ImportBrokerCsvResult(accepted, fundedDeposits, duplicates, skipped, snapshot)
    }

    private fun handle(line: BrokerCsvLine) {
        if (line.skipReason != null) {
            skipped += "Line ${line.sourceLine}: ${line.skipReason}"
            return
        }
        val request = requestFor(line)
        if (request == null) {
            skipped += describeSkip(line)
            return
        }
        if (isDuplicate(line)) {
            duplicates += 1
            return
        }
        persist(line, request)
    }

    private fun persist(line: BrokerCsvLine, request: LedgerEntryRequest) {
        val fundedId = fundIfNeeded(request)
        when (val result = applyLedger(snapshot, request)) {
            is LedgerEntryResult.Accepted -> accept(result)
            is LedgerEntryResult.Rejected -> {
                if (fundedId != null) undoFund(fundedId)
                skipped += "Line ${line.sourceLine}: ${result.reason}"
            }
        }
    }

    private fun fundIfNeeded(request: LedgerEntryRequest): String? {
        val asset = previewAsset(request) ?: return null
        val deposit = funder.depositFor(snapshot, request, asset) ?: return null
        val result = applyLedger(snapshot, deposit)
        if (result !is LedgerEntryResult.Accepted) return null
        accept(result)
        fundedDeposits += 1
        return result.transaction.id
    }

    private fun undoFund(txId: String) {
        snapshot = snapshot.copy(transactions = snapshot.transactions.filterNot { it.id == txId })
        accepted -= 1
        fundedDeposits -= 1
    }

    private fun accept(result: LedgerEntryResult.Accepted) {
        val assets = if (result.createdAsset) snapshot.assets + result.asset else snapshot.assets
        val fx = result.fxRate?.let { rate ->
            if (snapshot.fxRates.any { it.date == rate.date }) snapshot.fxRates else snapshot.fxRates + rate
        } ?: snapshot.fxRates
        snapshot = snapshot.copy(assets = assets, transactions = snapshot.transactions + result.transaction, fxRates = fx)
        accepted += 1
    }

    private fun requestFor(line: BrokerCsvLine): LedgerEntryRequest? {
        val type = line.type ?: return null
        val date = line.date ?: return null
        return requestOf(type, date, line)
    }

    private fun requestOf(type: TransactionType, date: java.time.LocalDate, line: BrokerCsvLine): LedgerEntryRequest? {
        if (type == TransactionType.DEPOSIT_CASH || type == TransactionType.WITHDRAWAL) {
            return LedgerEntryRequest(type, date, line.quantity, BigDecimal.ONE, BigDecimal.ZERO)
        }
        return holdingRequest(type, date, line)
    }

    private fun holdingRequest(type: TransactionType, date: java.time.LocalDate, line: BrokerCsvLine): LedgerEntryRequest? {
        val existing = matchAsset(line)
        if (existing != null && existing.baseCurrency != line.currency) return null
        if (type != TransactionType.BUY && existing == null) return null
        return LedgerEntryRequest(
            type = type,
            date = date,
            quantity = line.quantity,
            unitPriceNative = line.unitPriceNative,
            feesEur = line.feesEur,
            existingAssetId = existing?.id,
            newAsset = if (existing == null) draft(line) else null,
            eurPerUsd = line.eurPerUsd,
        )
    }

    private fun describeSkip(line: BrokerCsvLine): String {
        val existing = matchAsset(line)
        if (existing != null && existing.baseCurrency != line.currency) {
            return "Line ${line.sourceLine}: CSV ${line.currency} does not match holding ${existing.baseCurrency}."
        }
        return "Line ${line.sourceLine}: No holding for ${line.symbol.ifBlank { "row" }}."
    }

    private fun previewAsset(request: LedgerEntryRequest): Asset? {
        request.existingAssetId?.let { id -> return snapshot.assets.firstOrNull { it.id == id } }
        val draft = request.newAsset ?: return null
        return Asset("preview", draft.symbol, draft.name, draft.assetType, draft.baseCurrency, draft.isin, draft.quoteSymbol)
    }

    private fun matchAsset(line: BrokerCsvLine): Asset? {
        val isin = line.isin?.uppercase()
        if (!isin.isNullOrBlank()) {
            snapshot.assets.firstOrNull { it.isin?.uppercase() == isin }?.let { return it }
        }
        val symbol = line.symbol.uppercase()
        val quote = line.quoteSymbol?.uppercase()
        return snapshot.assets.firstOrNull { matchesSymbol(it, symbol, quote) }
    }

    private fun isDuplicate(line: BrokerCsvLine): Boolean {
        val fp = ImportFingerprints.of(line)
        val n = (seen[fp] ?: 0) + 1
        seen[fp] = n
        return n <= (already[fp] ?: 0)
    }

    private fun ordered(lines: List<BrokerCsvLine>): List<BrokerCsvLine> =
        lines.sortedWith(compareBy({ it.date ?: java.time.LocalDate.MAX }, { importRank(it.type) }, { it.sourceLine }))

    private fun importRank(type: TransactionType?): Int = type?.csvImportRank ?: 9

    private fun draft(line: BrokerCsvLine) =
        NewAssetDraft(line.symbol, line.name.ifBlank { line.symbol }, line.assetType, line.currency, line.isin, line.quoteSymbol)

    private fun matchesSymbol(asset: Asset, symbol: String, quote: String?): Boolean {
        if (asset.symbol.uppercase() == symbol || asset.feedSymbol.uppercase() == symbol) return true
        return quote != null && (asset.quoteSymbol?.uppercase() == quote || asset.feedSymbol.uppercase() == quote)
    }
}
