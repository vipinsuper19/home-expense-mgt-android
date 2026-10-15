package com.dhama.homefinance.data.local

import androidx.room.TypeConverter
import com.dhama.homefinance.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate

class Converters {
    /** Stored as ISO yyyy-MM-dd so string order == date order. */
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromType(type: TransactionType): String = type.name

    @TypeConverter
    fun toType(value: String): TransactionType = TransactionType.valueOf(value)
}
