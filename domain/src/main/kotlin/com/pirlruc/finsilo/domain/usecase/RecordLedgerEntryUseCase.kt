package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class NewAssetDraft(
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val baseCurrency: Currency,
    val isin: String? = null,
    val quoteSymbol: String? = null,
)

data class LedgerEntryRequest(
    val type: TransactionType,
    val date: LocalDate,
    val quantity: BigDecimal,
    val unitPriceNative: BigDecimal,
    val feesEur: BigDecimal,
    val existingAssetId: String? = null,
    val newAsset: NewAssetDraft? = null,
    val eurPerUsd: BigDecimal? = null,
)

sealed interface LedgerEntryResult {
    data class Accepted(val asset: Asset, val createdAsset: Boolean, val transaction: Transaction, val fxRate: CurrencyRate?) :
        LedgerEntryResult

    data class Rejected(val reason: String) : LedgerEntryResult
}

/**
 * Validates a Phase 2 ledger write against FIFO quantity (O2) and uninvested
 * cash (O3), then builds the row. Persistence is left to the repository.
 */
class RecordLedgerEntryUseCase(
    private val ledger: PositionLedger = PositionLedger(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    operator fun invoke(snapshot: PortfolioSnapshot, request: LedgerEntryRequest): LedgerEntryResult {
        val amountError = validateAmounts(request)
        if (amountError != null) return amountError
        val resolved = resolveAsset(snapshot, request) ?: return missingAsset(request)
        return acceptResolved(snapshot, request, resolved)
    }

    private fun acceptResolved(
        snapshot: PortfolioSnapshot,
        request: LedgerEntryRequest,
        resolved: Pair<Asset, Boolean>,
    ): LedgerEntryResult {
        val (asset, created) = resolved
        val typeError = validateType(asset, request.type)
        val rate = fxRate(asset, request, snapshot)
        val transaction = rate?.let { transactionFor(asset, request, it) }
        val ledgerError = transaction?.let { validateAgainstLedger(snapshot, asset, it) }
        return firstReject(typeError, rate, transaction, ledgerError)
            ?: LedgerEntryResult.Accepted(
                asset = asset,
                createdAsset = created,
                transaction = requireNotNull(transaction),
                fxRate = seededMtMFx(asset, request.date, requireNotNull(rate), snapshot),
            )
    }

    private fun firstReject(
        typeError: LedgerEntryResult.Rejected?,
        rate: BigDecimal?,
        transaction: Transaction?,
        ledgerError: LedgerEntryResult.Rejected?,
    ): LedgerEntryResult.Rejected? {
        if (typeError != null) return typeError
        if (rate == null) {
            return LedgerEntryResult.Rejected(
                "EUR per 1 USD is required for USD rows when no FX history is stored.",
            )
        }
        if (transaction == null) return missingAssetPlaceholder()
        return ledgerError
    }

    private fun missingAssetPlaceholder(): LedgerEntryResult.Rejected = LedgerEntryResult.Rejected("Choose an existing instrument.")

    private fun missingAsset(request: LedgerEntryRequest): LedgerEntryResult.Rejected = LedgerEntryResult.Rejected(
        when (request.type) {
            TransactionType.BUY -> "Choose an existing holding or enter a new instrument."
            TransactionType.DEPOSIT_CASH, TransactionType.WITHDRAWAL -> "Cash account is missing."
            else -> "Choose an existing instrument."
        },
    )

    private fun transactionFor(asset: Asset, request: LedgerEntryRequest, rate: BigDecimal): Transaction = Transaction(
        id = newId(),
        assetId = asset.id,
        date = request.date,
        type = request.type,
        quantity = request.quantity,
        unitPriceNative = request.unitPriceNative,
        exchangeRateAtExecution = rate,
        unitPriceEur = toEur(request.unitPriceNative, asset.baseCurrency, rate),
        feesEur = request.feesEur,
    )

    private fun validateAmounts(request: LedgerEntryRequest): LedgerEntryResult.Rejected? {
        val reason =
            when {
                request.quantity.signum() <= 0 -> "Quantity must be greater than zero."
                request.unitPriceNative.signum() < 0 -> "Price cannot be negative."
                request.feesEur.signum() < 0 -> "Fees cannot be negative."
                request.eurPerUsd != null && request.eurPerUsd.signum() <= 0 ->
                    "EUR per 1 USD must be greater than zero."
                else -> null
            }
        return reason?.let { LedgerEntryResult.Rejected(it) }
    }

    private fun resolveAsset(snapshot: PortfolioSnapshot, request: LedgerEntryRequest): Pair<Asset, Boolean>? = when (request.type) {
        TransactionType.DEPOSIT_CASH, TransactionType.WITHDRAWAL -> resolveCash(snapshot)
        TransactionType.BUY -> resolveBuy(snapshot, request)
        else ->
            request.existingAssetId
                ?.let { id -> snapshot.assets.firstOrNull { it.id == id } }
                ?.let { it to false }
    }

    private fun resolveCash(snapshot: PortfolioSnapshot): Pair<Asset, Boolean> {
        val existing = snapshot.assets.firstOrNull { it.assetType == AssetType.CASH }
        return if (existing != null) existing to false else cashAsset() to true
    }

    private fun resolveBuy(snapshot: PortfolioSnapshot, request: LedgerEntryRequest): Pair<Asset, Boolean>? {
        val existingId = request.existingAssetId
        if (!existingId.isNullOrBlank()) {
            return snapshot.assets.firstOrNull { it.id == existingId }?.let { it to false }
        }
        val draft = request.newAsset ?: return null
        val symbol = draft.symbol.trim()
        val name = draft.name.trim().ifBlank { symbol }
        if (symbol.isEmpty() || draft.assetType == AssetType.CASH) return null
        return Asset(
            id = "asset-${newId()}",
            symbol = symbol,
            name = name,
            assetType = draft.assetType,
            baseCurrency = draft.baseCurrency,
            isin = draft.isin?.trim()?.ifBlank { null },
            quoteSymbol = draft.quoteSymbol?.trim()?.ifBlank { null },
        ) to true
    }

    private fun validateType(asset: Asset, type: TransactionType): LedgerEntryResult.Rejected? =
        typeError(asset, type)?.let { LedgerEntryResult.Rejected(it) }

    private fun typeError(asset: Asset, type: TransactionType): String? {
        if (type == TransactionType.INTEREST) {
            return unless(asset.assetType.allowsInterest, "Interest applies to deposits, CTs, and PPR.")
        }
        if (type == TransactionType.DIVIDEND) {
            return unless(asset.assetType.allowsDividend, "Dividends apply to stocks, ETFs, and PPR.")
        }
        if (type == TransactionType.SELL || type == TransactionType.BUY) {
            return cashInstrumentError(asset, type)
        }
        return unless(asset.assetType == AssetType.CASH, "Cash movements must use the cash account.")
    }

    private fun cashInstrumentError(asset: Asset, type: TransactionType): String? {
        if (asset.assetType != AssetType.CASH) return null
        return if (type == TransactionType.SELL) {
            "Use Withdrawal for uninvested cash."
        } else {
            "Use Deposit for uninvested cash."
        }
    }

    private fun unless(ok: Boolean, message: String): String? = if (ok) null else message

    private fun fxRate(asset: Asset, request: LedgerEntryRequest, snapshot: PortfolioSnapshot): BigDecimal? {
        if (asset.baseCurrency == Currency.EUR) return BigDecimal.ONE
        return request.eurPerUsd ?: ledger.eurPerUsdOn(request.date, snapshot.fxRates)
    }

    /**
     * Execution FX already lives on [Transaction.exchangeRateAtExecution].
     * Seed [currency_history] only when that date has no MTM row, so a typed
     * trade rate cannot REPLACE a Frankfurter (or earlier) quote for the day.
     */
    private fun seededMtMFx(asset: Asset, date: LocalDate, rate: BigDecimal, snapshot: PortfolioSnapshot): CurrencyRate? {
        if (asset.baseCurrency != Currency.USD) return null
        if (snapshot.fxRates.any { it.date == date }) return null
        return CurrencyRate(date, rate)
    }

    private fun validateAgainstLedger(snapshot: PortfolioSnapshot, asset: Asset, transaction: Transaction): LedgerEntryResult.Rejected? {
        val prior = ledger.preceding(snapshot.transactions, transaction)
        return when (transaction.type) {
            TransactionType.SELL -> {
                val remaining = ledger.position(prior.filter { it.assetId == asset.id }).quantity
                if (transaction.quantity > remaining) {
                    LedgerEntryResult.Rejected(
                        "Sell quantity ${transaction.quantity.toPlainString()} exceeds remaining " +
                            "${remaining.stripTrailingZeros().toPlainString()}.",
                    )
                } else {
                    null
                }
            }
            TransactionType.WITHDRAWAL -> {
                val assetsById = snapshot.assets.associateBy { it.id } + (asset.id to asset)
                val cash = ledger.cashEur(prior, assetsById)
                val amount = transaction.notionalEur
                if (amount > cash) {
                    LedgerEntryResult.Rejected(
                        "Withdrawal ${amount.stripTrailingZeros().toPlainString()} EUR exceeds " +
                            "uninvested cash ${cash.stripTrailingZeros().toPlainString()} EUR.",
                    )
                } else {
                    null
                }
            }
            TransactionType.BUY -> {
                val assetsById = snapshot.assets.associateBy { it.id } + (asset.id to asset)
                val cash = ledger.cashEur(prior, assetsById)
                val cost = ledger.buyCostEur(transaction)
                if (cost > cash) {
                    LedgerEntryResult.Rejected(
                        "Buy ${cost.stripTrailingZeros().toPlainString()} EUR exceeds " +
                            "uninvested cash ${cash.stripTrailingZeros().toPlainString()} EUR.",
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun cashAsset(): Asset = Asset(
        id = CASH_ASSET_ID,
        symbol = "EUR-CASH",
        name = "Euro cash",
        assetType = AssetType.CASH,
        baseCurrency = Currency.EUR,
    )

    companion object {
        const val CASH_ASSET_ID: String = "asset-cash"
    }
}

fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
