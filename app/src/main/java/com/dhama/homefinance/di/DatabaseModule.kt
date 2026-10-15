package com.dhama.homefinance.di

import android.content.Context
import androidx.room.Room
import com.dhama.homefinance.data.local.AppDatabase
import com.dhama.homefinance.data.local.CategoryDao
import com.dhama.homefinance.data.local.OccurrenceDao
import com.dhama.homefinance.data.local.SummaryDao
import com.dhama.homefinance.data.local.TransactionListDao
import com.dhama.homefinance.data.local.SeedDefaultsCallback
import com.dhama.homefinance.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "home_finance.db"
        )
            .addCallback(SeedDefaultsCallback)
            // TODO(before first release): remove the destructive fallback and add real
            //  migrations (with MigrationTestHelper tests against app/schemas); otherwise a
            //  schema change would wipe users' data.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideOccurrenceDao(database: AppDatabase): OccurrenceDao = database.occurrenceDao()

    @Provides
    fun provideSummaryDao(database: AppDatabase): SummaryDao = database.summaryDao()

    @Provides
    fun provideTransactionListDao(database: AppDatabase): TransactionListDao = database.transactionListDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
