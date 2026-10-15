package com.dhama.homefinance.data

import com.dhama.homefinance.data.local.Converters
import com.dhama.homefinance.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun localDateRoundTripsAsIso() {
        val d = LocalDate.of(2026, 2, 28)
        assertEquals("2026-02-28", c.fromLocalDate(d))
        assertEquals(d, c.toLocalDate("2026-02-28"))
        assertNull(c.fromLocalDate(null))
        assertNull(c.toLocalDate(null))
    }

    @Test fun isoDateStringsSortChronologically() {
        val a = c.fromLocalDate(LocalDate.of(2026, 9, 30))!!
        val b = c.fromLocalDate(LocalDate.of(2026, 10, 1))!!
        assertTrue(a < b)
    }

    @Test fun instantAndTypeRoundTrip() {
        val i = Instant.ofEpochMilli(1_760_000_000_123)
        assertEquals(i, c.toInstant(c.fromInstant(i)))
        TransactionType.entries.forEach { assertEquals(it, c.toType(c.fromType(it))) }
    }
}
