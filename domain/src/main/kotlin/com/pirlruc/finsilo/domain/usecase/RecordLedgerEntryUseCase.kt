package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
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
    data class Accepted(
        val asset: Asset,
        val createdAsset: Boolean,
        val transaction: Transaction,
        val fxRate: CurrencyRate?,
    ) : LedgerEntryResult

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
        validateAmounts(request)?.let { return it }
        val resolved = resolveAsset(snapshot, request) ?: return LedgerEntryResult.Rejected(
            when (request.type) {
                TransactionType.BUY -> "Choose an existing holding or enter a new instrument."
                TransactionType.DEPOSIT_CASH, TransactionType.WITHDRAWAL -> "Cash account is missing."
                else -> "Choose an existing instrument."
            },
        )
        val (asset, created) = resolved
        validateType(asset, request.type)?.let { return it }

        val rate = fxRate(asset, request, snapshot)
        val unitPriceEur = toEur(request.unitPriceNative, asset.baseCurrency, rate)
        val transaction = Transaction(
            id = newId(),
            assetId = asset.id,
            date = request.date,
            type = request.type,
            quantity = request.quantity,
            unitPriceNative = request.unitPriceNative,
            exchangeRateAtExecution = rate,
            unitPriceEur = unitPriceEur,
            feesEur = request.feesEur,
        )
        validateAgainstLedger(snapshot, asset, transaction)?.let { return it }
        val storedFx =
            if (asset.baseCurrency == Currency.USD) CurrencyRate(request.date, rate) else null
        return LedgerEntryResult.Accepted(
            asset = asset,
            createdAsset = created,
            transaction = transaction,
            fxRate = storedFx,
        )
    }

    private fun validateAmounts(request: LedgerEntryRequest): LedgerEntryResult.Rejected? {
        if (request.quantity.signum() <= 0) return LedgerEntryResult.Rejected("Quantity must be greater than zero.")
        if (request.unitPriceNative.signum() < 0) return LedgerEntryResult.Rejected("Price cannot be negative.")
        if (request.feesEur.signum() < 0) return LedgerEntryResult.Rejected("Fees cannot be negative.")
        if (request.eurPerUsd != null && request.eurPerUsd.signum() <= 0) {
            return LedgerEntryResult.Rejected("EUR per 1 USD must be greater than zero.")
        }
        return null
    }

    private fun resolveAsset(
        snapshot: PortfolioSnapshot,
        request: LedgerEntryRequest,
    ): Pair<Asset, Boolean>? {
        return when (request.type) {
            TransactionType.DEPOSIT_CASH, TransactionType.WITHDRAWAL -> {
                val existing = snapshot.assets.firstOrNull { it.assetType == AssetType.CASH }
                if (existing != null) existing to false
                else cashAsset() to true
            }
            TransactionType.BUY -> {
                val existingId = request.existingAssetId
                if (!existingId.isNullOrBlank()) {
                    snapshot.assets.firstOrNull { it.id == existingId }?.let { it to false }
                } else {
                    val draft = request.newAsset ?: return null
                    val symbol = draft.symbol.trim()
                    val name = draft.name.trim().ifBlank { symbol }
                    if (symbol.isEmpty()) return null
                    if (draft.assetType == AssetType.CASH) return null
                    Asset(
                        id = "asset-${newId()}",
                        symbol = symbol,
                        name = name,
                        assetType = draft.assetType,
                        baseCurrency = draft.baseCurrency,
                        isin = draft.isin?.trim()?.ifBlank { null },
                        quoteSymbol = draft.quoteSymbol?.trim()?.ifBlank { null },
                    ) to true
                }
            }
            else -> {
                val id = request.existingAssetId ?: return null
                snapshot.assets.firstOrNull { it.id == id }?.let { it to false }
            }
        }
    }

    private fun validateType(asset: Asset, type: TransactionType): LedgerEntryResult.Rejected? =
        when (type) {
            TransactionType.INTEREST ->
                if (asset.assetType.allowsInterest) null
                else LedgerEntryResult.Rejected("Interest applies to deposits, CTs, and PPR.")
            TransactionType.DIVIDEND ->
                if (asset.assetType.allowsDividend) null
                else LedgerEntryResult.Rejected("Dividends apply to stocks, ETFs, and PPR.")
            TransactionType.SELL ->
                if (asset.assetType == AssetType.CASH) {
                    LedgerEntryResult.Rejected("Use Withdrawal for uninvested cash.")
                } else {
                    null
                }
            TransactionType.BUY ->
                if (asset.assetType == AssetType.CASH) {
                    LedgerEntryResult.Rejected("Use Deposit for uninvested cash.")
                } else {
                    null
                }
            TransactionType.DEPOSIT_CASH, TransactionType.WITHDRAWAL ->
                if (asset.assetType == AssetType.CASH) null
                else LedgerEntryResult.Rejected("Cash movements must use the cash account.")
        }

    private fun fxRate(asset: Asset, request: LedgerEntryRequest, snapshot: PortfolioSnapshot): BigDecimal {
        if (asset.baseCurrency == Currency.EUR) return BigDecimal.ONE
        return request.eurPerUsd ?: ledger.eurPerUsdOn(request.date, snapshot.fxRates)
    }

    private fun validateAgainstLedger(
        snapshot: PortfolioSnapshot,
        asset: Asset,
        transaction: Transaction,
    ): LedgerEntryResult.Rejected? {
        val prior = ledger.transactionsOnOrBefore(snapshot.transactions, transaction.date)
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
            else -> null
        }
    }

    private fun cashAsset(): Asset =
        Asset(
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

fun parseDecimal(raw: String): BigDecimal? {
    val trimmed = raw.trim().replace(',', '.')
    if (trimmed.isEmpty()) return null
    return runCatching { MoneyMath.bd(trimmed) }.getOrNull()
}

fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
