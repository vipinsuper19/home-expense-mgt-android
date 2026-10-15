package com.dhama.homefinance.ui.transaction

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Pure helpers for the transaction form, kept out of Compose so they're unit-testable. */
object TransactionFormLogic {
    private val quantityPattern = Regex("""^\d{1,9}(\.\d{0,3})?$""")

    /** "1.5" -> 1500 (thousandths). Blank -> null (optional). Invalid or 0 -> [Invalid]. */
    fun parseQuantityMilli(input: String): QuantityResult {
        val s = input.trim()
        if (s.isEmpty()) return QuantityResult.Empty
        if (!quantityPattern.matches(s)) return QuantityResult.Invalid
        val parts = s.split('.')
        val milli = parts[0].toLong() * 1000 + parts.getOrNull(1).orEmpty().padEnd(3, '0').toLong()
        return if (milli > 0) QuantityResult.Valid(milli) else QuantityResult.Invalid
    }

    fun formatQuantityMilli(milli: Long): String {
        val whole = milli / 1000
        val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
    }

    /** For editing: paise -> "1250.5" style text that Money parses back to the same value. */
    fun formatPaiseForInput(paise: Long): String {
        val rupees = paise / 100
        val frac = (paise % 100).toString().padStart(2, '0')
        return if (frac == "00") rupees.toString() else "$rupees.$frac"
    }

    /**
     * Material3 DatePicker works in UTC-midnight millis. Converting in UTC (not the device
     * zone) keeps the picked calendar day exactly, whatever the phone's time zone.
     */
    fun pickerMillisToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun dateToPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    sealed interface QuantityResult {
        data object Empty : QuantityResult
        data object Invalid : QuantityResult
        data class Valid(val milli: Long) : QuantityResult
    }
}
