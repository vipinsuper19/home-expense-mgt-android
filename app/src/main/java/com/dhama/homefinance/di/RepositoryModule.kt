package com.dhama.homefinance.di

import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.RoomCategoryRepository
import com.dhama.homefinance.data.repository.RoomSummaryRepository
import com.dhama.homefinance.data.repository.RoomTransactionListRepository
import com.dhama.homefinance.data.repository.TransactionListRepository
import com.dhama.homefinance.data.repository.RoomTransactionRepository
import com.dhama.homefinance.data.repository.SummaryRepository
import com.dhama.homefinance.data.repository.TransactionRepository
import com.dhama.homefinance.domain.recurrence.OccurrenceMaterializer
import com.dhama.homefinance.domain.recurrence.RoomOccurrenceMaterializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindOccurrenceMaterializer(impl: RoomOccurrenceMaterializer): OccurrenceMaterializer

    @Binds
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    abstract fun bindSummaryRepository(impl: RoomSummaryRepository): SummaryRepository

    @Binds
    abstract fun bindTransactionListRepository(impl: RoomTransactionListRepository): TransactionListRepository
}
