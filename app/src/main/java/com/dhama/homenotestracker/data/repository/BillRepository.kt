package com.dhama.homenotestracker.data.repository

import com.dhama.homenotestracker.data.local.BillDao
import com.dhama.homenotestracker.data.model.Bill
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao
) {
    fun getAllBills(): Flow<List<Bill>> = billDao.getAllBills()

    suspend fun getBillById(id: Long): Bill? = billDao.getBillById(id)

    suspend fun insertBill(bill: Bill): Long = billDao.insertBill(bill)

    suspend fun updateBill(bill: Bill) = billDao.updateBill(bill)

    suspend fun deleteBill(bill: Bill) = billDao.deleteBill(bill)

    suspend fun updateBillPaymentStatus(id: Long, isPaid: Boolean) = billDao.updateBillPaymentStatus(id, isPaid)
}
