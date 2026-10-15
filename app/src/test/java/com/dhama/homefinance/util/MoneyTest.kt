package com.dhama.homefinance.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesWholeAndFractionalRupees() {
        assertEquals(10000L, Money.parseRupeesToPaise("100"))
        assertEquals(10050L, Money.parseRupeesToPaise("100.5"))
        assertEquals(10005L, Money.parseRupeesToPaise("100.05"))
        assertEquals(125050L, Money.parseRupeesToPaise("₹ 1,250.50"))
        assertEquals(1L, Money.parseRupeesToPaise("0.01"))
        assertEquals(10000L, Money.parseRupeesToPaise("100."))
    }

    @Test fun rejectsZero() {
        listOf("0", "0.00", "0.0", "0.", "₹ 0", "000", "0,000.00").forEach {
            assertNull(it, Money.parseRupeesToPaise(it))
        }
    }

    @Test fun rejectsInvalidInput() {
        listOf("", "abc", "-5", "1.234", "1e3", ".5", "12345678901234").forEach {
            assertNull(it, Money.parseRupeesToPaise(it))
        }
    }

    @Test fun noFloatingPointDrift() {
        // 0.1 + 0.2 style inputs stay exact
        assertEquals(30L, Money.parseRupeesToPaise("0.1")!! + Money.parseRupeesToPaise("0.2")!!)
    }

    @Test fun formatsIndianGrouping() {
        assertEquals("₹0.00", Money.formatPaise(0))
        assertEquals("₹999.05", Money.formatPaise(99905))
        assertEquals("₹1,000.00", Money.formatPaise(100000))
        assertEquals("₹1,23,456.78", Money.formatPaise(12345678))
        assertEquals("₹1,00,00,000.00", Money.formatPaise(1_000_000_000))
        assertEquals("-₹50.00", Money.formatPaise(-5000))
        assertEquals("12.30", Money.formatPaise(1230, withSymbol = false))
    }
}
