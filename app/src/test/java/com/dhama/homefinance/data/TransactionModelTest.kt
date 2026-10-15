package com.dhama.homefinance.data

import com.dhama.homefinance.data.model.Transaction
import com.dhama.homefinance.data.model.TransactionType
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransactionModelTest {
    private fun tx(amount: Long = 100, qty: Long? = null, rate: Long? = null) = Transaction(
        type = TransactionType.EXPENSE, categoryId = "c", amountPaise = amount,
        quantityMilli = qty, unitRatePaise = rate, date = LocalDate.of(2026, 10, 15)
    )

    @Test fun idsAreRandomUuids() {
        val a = tx(); val b = tx()
        UUID.fromString(a.id)
        assertNotEquals(a.id, b.id)
    }

    @Test fun updatedAtDefaultsToCreatedAt() {
        val t = tx()
        assertEquals(t.createdAt, t.updatedAt)
    }

    @Test(expected = IllegalArgumentException::class) fun zeroAmountRejected() { tx(amount = 0) }
    @Test(expected = IllegalArgumentException::class) fun negativeAmountRejected() { tx(amount = -1) }
    @Test(expected = IllegalArgumentException::class) fun zeroQuantityRejected() { tx(qty = 0) }
    @Test(expected = IllegalArgumentException::class) fun negativeRateRejected() { tx(rate = -1) }
}
