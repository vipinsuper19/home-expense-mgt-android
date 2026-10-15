package com.dhama.homefinance.di

import com.dhama.homefinance.data.repository.CategoryRepository
import com.dhama.homefinance.data.repository.RoomCategoryRepository
import com.dhama.homefinance.data.repository.RoomTransactionRepository
import com.dhama.homefinance.data.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository
}
