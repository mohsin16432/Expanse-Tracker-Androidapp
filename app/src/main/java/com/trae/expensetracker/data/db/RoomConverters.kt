package com.trae.expensetracker.data.db

import androidx.room.TypeConverter
import com.trae.expensetracker.data.model.DataSourceType
import com.trae.expensetracker.data.model.MerchantRuleType
import com.trae.expensetracker.data.model.PendingImportStatus
import com.trae.expensetracker.data.model.TransactionDirection
import com.trae.expensetracker.data.model.TransactionType

object RoomConverters {
    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toDataSourceType(value: String): DataSourceType = DataSourceType.valueOf(value)

    @TypeConverter
    fun fromDataSourceType(value: DataSourceType): String = value.name

    @TypeConverter
    fun toPendingImportStatus(value: String): PendingImportStatus = PendingImportStatus.valueOf(value)

    @TypeConverter
    fun fromPendingImportStatus(value: PendingImportStatus): String = value.name

    @TypeConverter
    fun toMerchantRuleType(value: String): MerchantRuleType = MerchantRuleType.valueOf(value)

    @TypeConverter
    fun fromMerchantRuleType(value: MerchantRuleType): String = value.name
}
