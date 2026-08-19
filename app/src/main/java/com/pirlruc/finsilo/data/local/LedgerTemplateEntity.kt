package com.pirlruc.finsilo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.TransactionType

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
