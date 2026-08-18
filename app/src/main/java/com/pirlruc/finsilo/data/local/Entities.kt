package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.WatchlistItem
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") val assetId: String,
    val symbol: String,
    val name: String,
    @ColumnInfo(name = "asset_type") val assetType: AssetType,
    @ColumnInfo(name = "base_currency") val baseCurrency: Currency,
    val isin: String? = null,
    @ColumnInfo(name = "quote_symbol") val quoteSymbol: String? = null,
) {
    fun toDomain(): Asset = Asset(assetId, symbol, name, assetType, baseCurrency, isin, quoteSymbol)

    companion object {
        fun from(asset: Asset) = AssetEntity(
            asset.id,
            asset.symbol,
            asset.name,
            asset.assetType,
            asset.baseCurrency,
            asset.isin,
            asset.quoteSymbol,
        )
    }
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("asset_id"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "asset_id") val assetId: String,
    val date: LocalDate,
    val type: TransactionType,
    val quantity: BigDecimal,
    @ColumnInfo(name = "unit_price_native") val unitPriceNative: BigDecimal,
    @ColumnInfo(name = "exchange_rate_at_execution") val exchangeRateAtExecution: BigDecimal,
    @ColumnInfo(name = "unit_price_eur") val unitPriceEur: BigDecimal,
    @ColumnInfo(name = "fees_eur") val feesEur: BigDecimal,
    @ColumnInfo(name = "ledger_sequence", defaultValue = "0") val sequence: Long = 0,
) {
    fun toDomain(): Transaction = Transaction(
        id = transactionId,
        assetId = assetId,
        date = date,
        type = type,
        quantity = quantity,
        unitPriceNative = unitPriceNative,
        exchangeRateAtExecution = exchangeRateAtExecution,
        unitPriceEur = unitPriceEur,
        feesEur = feesEur,
        sequence = sequence,
    )

    companion object {
        fun from(tx: Transaction) = TransactionEntity(
            transactionId = tx.id,
            assetId = tx.assetId,
            date = tx.date,
            type = tx.type,
            quantity = tx.quantity,
            unitPriceNative = tx.unitPriceNative,
            exchangeRateAtExecution = tx.exchangeRateAtExecution,
            unitPriceEur = tx.unitPriceEur,
            feesEur = tx.feesEur,
            sequence = tx.sequence,
        )
    }
}

@Entity(
    tableName = "daily_market_data",
    primaryKeys = ["asset_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("asset_id"), Index("date")],
)
data class DailyMarketDataEntity(
    @ColumnInfo(name = "asset_id") val assetId: String,
    val date: LocalDate,
    @ColumnInfo(name = "closing_price_native") val closingPriceNative: BigDecimal,
    @ColumnInfo(name = "analyst_rating") val analystRating: AnalystRating,
    @ColumnInfo(name = "sma_50") val sma50: BigDecimal?,
    @ColumnInfo(name = "sma_200") val sma200: BigDecimal?,
) {
    fun toDomain(): DailyMarketData = DailyMarketData(assetId, date, closingPriceNative, analystRating, sma50, sma200)

    companion object {
        fun from(row: DailyMarketData) = DailyMarketDataEntity(
            assetId = row.assetId,
            date = row.date,
            closingPriceNative = row.closingPriceNative,
            analystRating = row.analystRating,
            sma50 = row.sma50,
            sma200 = row.sma200,
        )
    }
}

@Entity(tableName = "currency_history")
data class CurrencyRateEntity(@PrimaryKey val date: LocalDate, @ColumnInfo(name = "eur_usd_rate") val eurPerUsd: BigDecimal) {
    fun toDomain(): CurrencyRate = CurrencyRate(date, eurPerUsd)

    companion object {
        fun from(row: CurrencyRate) = CurrencyRateEntity(row.date, row.eurPerUsd)
    }
}

@Entity(tableName = "target_allocation")
data class TargetAllocationEntity(
    @PrimaryKey @ColumnInfo(name = "asset_type") val assetType: AssetType,
    @ColumnInfo(name = "weight_percent") val weightPercent: BigDecimal,
) {
    fun toDomain(): TargetAllocation = TargetAllocation(assetType, weightPercent)

    companion object {
        fun from(row: TargetAllocation) = TargetAllocationEntity(row.assetType, row.weightPercent)
    }
}

@Entity(tableName = "nav_history")
data class NavHistoryEntity(@PrimaryKey val date: LocalDate, @ColumnInfo(name = "value_eur") val valueEur: BigDecimal) {
    fun toDomain() = NavPoint(date, valueEur)

    companion object {
        fun from(point: NavPoint) = NavHistoryEntity(point.date, point.valueEur)
    }
}

@Entity(tableName = "nav_rebuild_state")
data class NavRebuildStateEntity(
    @PrimaryKey val id: Int = 1,
    val fingerprint: String,
    @ColumnInfo(name = "as_of") val asOf: LocalDate,
    @ColumnInfo(name = "rebuilt_at_ms") val rebuiltAtMs: Long,
)

@Entity(
    tableName = "price_alert_threshold",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PriceAlertThresholdEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") val assetId: String,
    @ColumnInfo(name = "eur_level") val eurLevel: BigDecimal? = null,
    @ColumnInfo(name = "percent_move") val percentMove: BigDecimal? = null,
) {
    fun toDomain() = PriceAlertThreshold(assetId, eurLevel, percentMove)

    companion object {
        fun from(row: PriceAlertThreshold) = PriceAlertThresholdEntity(row.assetId, row.eurLevel, row.percentMove)
    }
}

@Entity(tableName = "ledger_template")
data class LedgerTemplateEntity(
    @PrimaryKey @ColumnInfo(name = "template_id") val templateId: String,
    val label: String,
    val type: TransactionType,
    @ColumnInfo(name = "asset_id") val assetId: String? = null,
    val quantity: String = "",
    @ColumnInfo(name = "unit_price_native") val unitPriceNative: String = "",
    @ColumnInfo(name = "fees_eur") val feesEur: String = "0",
) {
    fun toDomain() = LedgerTemplate(
        templateId,
        label,
        type,
        assetId,
        quantity,
        unitPriceNative,
        feesEur,
    )

    companion object {
        fun from(row: LedgerTemplate) =
            LedgerTemplateEntity(row.id, row.label, row.type, row.assetId, row.quantity, row.unitPriceNative, row.feesEur)
    }
}

@Entity(tableName = "watchlist_item")
data class WatchlistItemEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    val symbol: String,
    val name: String,
    @ColumnInfo(name = "asset_type") val assetType: AssetType,
    @ColumnInfo(name = "base_currency") val baseCurrency: Currency,
    @ColumnInfo(name = "quote_symbol") val quoteSymbol: String? = null,
) {
    fun toDomain() = WatchlistItem(
        itemId,
        symbol,
        name,
        assetType,
        baseCurrency,
        quoteSymbol,
    )

    companion object {
        fun from(row: WatchlistItem) = WatchlistItemEntity(row.id, row.symbol, row.name, row.assetType, row.baseCurrency, row.quoteSymbol)
    }
}

@Entity(
    tableName = "watchlist_quote",
    primaryKeys = ["item_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = WatchlistItemEntity::class,
            parentColumns = ["item_id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_id")],
)
data class WatchlistQuoteEntity(
    @ColumnInfo(name = "item_id") val itemId: String,
    val date: LocalDate,
    @ColumnInfo(name = "closing_price_native") val closingPriceNative: BigDecimal,
    @ColumnInfo(name = "analyst_rating") val analystRating: AnalystRating,
    @ColumnInfo(name = "sma_50") val sma50: BigDecimal?,
    @ColumnInfo(name = "sma_200") val sma200: BigDecimal?,
) {
    fun toDomain() = DailyMarketData(itemId, date, closingPriceNative, analystRating, sma50, sma200)

    companion object {
        fun from(row: DailyMarketData) =
            WatchlistQuoteEntity(row.assetId, row.date, row.closingPriceNative, row.analystRating, row.sma50, row.sma200)
    }
}
