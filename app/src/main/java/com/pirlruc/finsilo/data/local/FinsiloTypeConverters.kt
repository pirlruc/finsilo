package com.pirlruc.finsilo.data.local

import androidx.room.TypeConverter
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

class FinsiloTypeConverters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun assetTypeToString(value: AssetType?): String? = value?.name

    @TypeConverter
    fun stringToAssetType(value: String?): AssetType? = value?.let(AssetType::valueOf)

    @TypeConverter
    fun currencyToString(value: Currency?): String? = value?.name

    @TypeConverter
    fun stringToCurrency(value: String?): Currency? = value?.let(Currency::valueOf)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun stringToTransactionType(value: String?): TransactionType? = value?.let(TransactionType::valueOf)

    @TypeConverter
    fun ratingToInt(value: AnalystRating?): Int? = value?.code

    @TypeConverter
    fun intToRating(value: Int?): AnalystRating? = value?.let(AnalystRating::fromCode)
}
