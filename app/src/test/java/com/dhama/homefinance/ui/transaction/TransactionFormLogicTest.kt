package com.dhama.homefinance.ui.transaction

import com.dhama.homefinance.ui.transaction.TransactionFormLogic.QuantityResult
import com.dhama.homefinance.util.Money
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFormLogicTest {
    @Test fun quantityParsing() {
        assertEquals(QuantityResult.Empty, TransactionFormLogic.parseQuantityMilli("  "))
        assertEquals(QuantityResult.Valid(1500), TransactionFormLogic.parseQuantityMilli("1.5"))
        assertEquals(QuantityResult.Valid(2), TransactionFormLogic.parseQuantityMilli("0.002"))
        listOf("0", "0.000", "-1", "1.2345", "abc", ".5").forEach {
            assertEquals(it, QuantityResult.Invalid, TransactionFormLogic.parseQuantityMilli(it))
        }
        assertEquals("1.5", TransactionFormLogic.formatQuantityMilli(1500))
        assertEquals("2", TransactionFormLogic.formatQuantityMilli(2000))
    }

    @Test fun paiseInputRoundTripsThroughMoney() {
        listOf(1L, 50L, 100L, 125050L, 99_999_99L).forEach {
            assertEquals(it, Money.parseRupeesToPaise(TransactionFormLogic.formatPaiseForInput(it)))
        }
    }

    @Test fun pickerDateIsUtcAndZoneIndependent() {
        val original = TimeZone.getDefault()
        try {
            listOf("Asia/Kolkata", "America/Los_Angeles", "Pacific/Kiritimati", "UTC").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2028, 2, 29), LocalDate.of(2026, 12, 31)).forEach { d ->
                    assertEquals("$zone $d", d, TransactionFormLogic.pickerMillisToDate(TransactionFormLogic.dateToPickerMillis(d)))
                }
            }
            // Material DatePicker gives UTC midnight; 2026-10-15T00:00Z must be 15 Oct, not 14 Oct in LA.
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(LocalDate.of(2026, 10, 15), TransactionFormLogic.pickerMillisToDate(Instant.parse("2026-10-15T00:00:00Z").toEpochMilli()))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
