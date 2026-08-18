package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

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
