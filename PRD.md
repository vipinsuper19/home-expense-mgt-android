# Home Income & Expense Manager

## Android MVP Product Requirements Document (PRD)

**Version:** 1.0\
**Date:** October 2026\
**Status:** Finalized MVP scope\
**Platform:** Native Android\
**Primary user:** One person managing personal household finances\
**Default currency:** Indian Rupee (INR, ₹)

------------------------------------------------------------------------

## 1. Product Summary

Home Income & Expense Manager is a simple, offline-first Android
application for tracking household income, daily expenses, recurring
transactions, reminders, and monthly financial summaries.

The product is designed around three everyday actions:

1.  Record spending quickly.
2.  Record income, especially monthly shop rent.
3.  Review actual income, expenses, and the resulting net balance.

The app should remain easy to use. It is not intended to be a full
accounting package or a banking application.

## 2. Product Goals

-   Make recording a common expense take only a few taps.
-   Track daily items such as milk, including quantity and rate.
-   Track expected monthly income, including rent from a shop.
-   Distinguish expected transactions from actual money received or
    spent.
-   Remind the user about recurring expenses and expected income.
-   Provide trustworthy monthly totals and category-level summaries.
-   Keep all core functionality available offline.
-   Allow the user to back up, restore, and export data.

## 3. Final Product Decisions

  Area              Decision
  ----------------- -------------------------------------------
  Platform          Native Android
  Language and UI   Kotlin, Jetpack Compose, Material 3
  Architecture      MVVM, repositories, domain/use-case layer
  Local storage     Room database
  Preferences       DataStore
  Background work   WorkManager
  Notifications     Local Android notifications
  Backup            Versioned JSON backup and restore
  Export            CSV; optional PDF report
  Authentication    None in MVP
  Backend/cloud     None in MVP
  Financial scope   Income, expenses, and net balance only

## 4. MVP Scope

### Included

-   Dashboard with today's totals, monthly income, monthly expenses, net
    balance, recent transactions, and upcoming items.
-   Add, edit, delete, search, and filter income and expense
    transactions.
-   Default and custom categories.
-   Quick entry for common transactions.
-   Daily milk tracking with quantity, rate, amount, skipped days, and
    monthly totals.
-   Recurring schedules: daily, weekly, monthly, yearly, and custom
    intervals.
-   Expected shop rent and other recurring income.
-   Partial rent receipts and outstanding balance tracking.
-   Bill due dates, reminders, overdue status, and payment history.
-   Monthly reports and previous-month comparisons.
-   JSON backup/restore and CSV export.
-   Currency and notification settings.

### Explicitly excluded

-   Cloud synchronization and multi-user access.
-   Bank account or UPI integrations.
-   SMS transaction reading.
-   Investments, debt management, and AI financial advice.
-   Business accounting, inventory, and double-entry bookkeeping.
-   Complex budgeting features in the initial MVP.
-   Mandatory account registration.

## 5. Navigation and Screen Specifications

The primary bottom navigation has four destinations: **Home,
Transactions, Recurring, Reports**. Settings are available from the Home
toolbar.

### 5.1 Home Dashboard

Display: - Current month selector. - Actual income for the selected
month. - Actual expenses for the selected month. - Net balance = actual
income minus actual expenses. - Today's income and expense totals. -
Recent transactions. - Upcoming and overdue scheduled items. - Quick
actions for Add Expense and Add Income.

Rules: - Expected income is not included in actual income. - Unpaid
bills are not included in actual expenses. - Clearly label actual and
expected figures. - Default to the current month.

### 5.2 Add Transaction

Fields: - Type: Income or Expense. - Amount: required, positive value. -
Category: required. - Description: optional. - Date: defaults to
today. - Quantity and unit rate: optional, used for milk tracking. -
Notes: optional.

Behavior: - Use a numeric keypad for amount entry. - Validate input
before saving. - Remember recent categories where useful. - Support edit
and delete, with confirmation for deletion. - Prevent accidental
duplicate saves from rapid taps.

### 5.3 Transactions and History

-   Chronological list of transactions.
-   Filters for date/month, type, and category.
-   Search by description.
-   Daily totals and monthly summary.
-   Open a transaction to view, edit, or delete it.
-   Visually distinguish income from expenses.

### 5.4 Recurring Schedules

Each schedule displays: - Name and transaction type. - Amount or
quantity/rate configuration. - Frequency and next due date. - Reminder
settings. - Active, paused, or ended status. - Last occurrence and
occurrence history.

Actions: - Create, edit, pause, resume, stop, mark paid, mark received,
skip, and view history.

Schedule fields include start date, frequency, interval, optional end
date, due day/date rule, reminder time, and expected amount. Monthly
schedules need a defined rule for months without the configured day.

### 5.5 Daily Milk Tracker

-   One entry per date for a given milk schedule.
-   Quantity in litres and price per litre.
-   Amount = quantity × unit rate.
-   Skipped days and missed-entry backfilling.
-   Correct quantity or rate without changing historical entries.
-   Month-to-date quantity and expense totals.
-   Support both a default rate and variable daily rates.

### 5.6 Monthly Reports

Include: 1. Actual income total. 2. Actual expense total. 3. Net
balance. 4. Category-wise expense breakdown. 5. Daily spending trend. 6.
Income sources, including shop rent. 7. Milk quantity and monthly cost.
8. Previous-month comparison. 9. CSV export and optional PDF export.

Reports use transaction dates, not the date the transaction was entered.

### 5.7 Settings and Data Management

-   Currency, default INR.
-   Categories and icons.
-   Default reminder time.
-   Notification permission status.
-   JSON backup and restore.
-   CSV transaction export.
-   Optional PDF report export.
-   App version and local-data information.

Use Android's system file picker for backup and restore. Validate
backups before restoring. Warn before replacing existing data.

## 6. Recurring Transaction Rules

  -----------------------------------------------------------------------
  Scenario                            Required behavior
  ----------------------------------- -----------------------------------
  Daily milk                          Generate an expected occurrence or
                                      reminder; record actual quantity
                                      and amount when confirmed.

  Monthly electricity bill            Generate expected bill and
                                      reminder; track unpaid/paid status.

  Shop rent                           Generate expected income; record
                                      actual receipts separately.

  Missed reminder                     Keep occurrence pending or overdue;
                                      never silently mark it paid.

  Skipped occurrence                  Mark skipped without creating an
                                      actual transaction.

  Amount changed                      Apply new amount to future
                                      occurrences only.

  Schedule paused                     Stop generating new occurrences and
                                      reminders until resumed.

  App restarted                       Reconcile overdue occurrences
                                      without duplicates.

  Month-end                           Calculate reports from actual
                                      transactions only.
  -----------------------------------------------------------------------

Use Room as the source of truth. WorkManager can reconcile schedules and
reminders, but execution is not guaranteed at an exact minute.
Exact-alarm APIs should be considered only if strict clock-time delivery
becomes a requirement.

Rebuild pending reminders after reboot, schedule changes, and relevant
date/time changes. Store due dates as local calendar dates to avoid
shifting daily entries across time-zone changes.

## 7. Logical Data Model

### 7.1 Category

Fields: - `id` - `name` - `type` - `icon` - `isDefault` - `isActive`

Groups transactions into categories such as Groceries, Milk, Bills, Shop
Rent, and Salary.

### 7.2 RecurringSchedule

Fields: - `id` - `name` - `type` - `categoryId` - `amountPaise` -
`frequency` - `interval` - `startDate` - `endDate` - `nextDueDate` -
`reminderEnabled` - `reminderTime` - `status`

Defines a recurring expected income or expense.

### 7.3 Transaction

Fields: - `id` - `type` - `categoryId` - `scheduleId` (nullable) -
`occurrenceId` (nullable) - `amountPaise` - `quantity` (nullable) -
`unitRatePaise` (nullable) - `description` - `date` - `notes` -
`createdAt` - `updatedAt`

Stores actual income and expenses. Store money as integer paise to avoid
floating-point rounding errors.

### 7.4 ScheduledOccurrence

Fields: - `id` - `scheduleId` - `dueDate` - `expectedAmountPaise` -
`status` - `reminderState`

Tracks each occurrence as upcoming, completed, partial, skipped, or
overdue. It may be linked to more than one transaction to support
partial receipts.

### 7.5 AppSettings

Fields: - `key` - `value`

Stores user preferences such as currency and reminder defaults.

### Database Integrity Rules

-   Use UUIDs or equivalent unique IDs.
-   Use foreign keys for categories and schedules.
-   Add a unique constraint on `(scheduleId, dueDate)` for generated
    occurrences.
-   Use a database transaction when completing an occurrence and
    creating linked transactions.
-   Allow multiple receipt transactions for one occurrence when partial
    payments are supported.
-   Prevent duplicate receipt creation through explicit confirmation and
    idempotent operation handling.
-   Preserve historical transaction amounts when schedule templates
    change.
-   Use `INCOME` and `EXPENSE` types; do not represent expense types
    through negative amounts.
-   Store transaction date separately from creation/update timestamps.
-   Calculate outstanding amount as expected amount minus linked actual
    receipts, with appropriate validation.

## 8. Shop Rent Example

Expected monthly rent: ₹15,000, due on the fifth day of the month.

1.  Generate a scheduled income occurrence for ₹15,000.
2.  Show it as expected income, not received income.
3.  If ₹10,000 arrives, record ₹10,000 as actual income and show ₹5,000
    outstanding.
4.  When the remaining ₹5,000 arrives, record the second receipt and
    close the occurrence.
5.  Monthly actual income includes only the money actually received
    during the selected month.

## 9. Architecture

``` text
Jetpack Compose Screens
        |
    ViewModels
        |
    Repositories
        |
    Room Database
        |
  -------------------------
  |                       |
Recurring Engine       Backup/Export
  |
WorkManager
  |
Android Notifications
```

Suggested project structure:

``` text
app/src/main/java/com/example/homefinance/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   └── entity/
│   ├── repository/
│   └── backup/
├── domain/
│   ├── model/
│   ├── usecase/
│   └── recurrence/
├── notifications/
│   ├── ReminderScheduler.kt
│   └── NotificationHelper.kt
├── ui/
│   ├── home/
│   ├── transactions/
│   ├── recurring/
│   ├── milk/
│   ├── reports/
│   └── settings/
├── navigation/
└── MainActivity.kt
```

Recommended libraries and patterns: - Kotlin, Jetpack Compose, Material
3. - Room, Kotlin Coroutines and Flow. - ViewModels and repositories. -
DataStore for preferences. - WorkManager for background
reconciliation. - Hilt for dependency injection if appropriate. -
Compose-compatible chart library for reports.

## 10. Development Task Order

### T1 --- Project Setup and Architecture

-   Configure Android project, Kotlin, Compose, Material 3, and
    navigation.
-   Establish package structure, conventions, error handling, and
    version control.
-   Verify the app builds.

**Deliverable:** Working application shell.

### T2 --- Database and Financial Rules

-   Implement Room entities, DAOs, repositories, and migrations.
-   Implement category and transaction CRUD.
-   Test income, expense, and monthly net calculations.
-   Validate money precision and date handling.

**Deliverable:** Reliable local financial data layer.

### T3 --- Dashboard and Transactions

-   Build dashboard and transaction forms.
-   Implement transaction history, filters, search, edit, and delete.
-   Add default categories and initial settings.

**Deliverable:** Usable manual expense tracker.

### T4 --- Recurring Schedule Engine

-   Implement schedules and occurrence generation.
-   Support daily, weekly, monthly, yearly, and custom intervals.
-   Handle month-end dates, edits, pauses, skipped occurrences, overdue
    items, and duplicate prevention.

**Deliverable:** Reliable recurring transaction engine.

### T5 --- Milk Tracking and Shop Rent

-   Build milk quick-entry and monthly totals.
-   Implement expected rent, partial receipts, and outstanding balances.
-   Keep expected and actual amounts separate.

**Deliverable:** Both key recurring use cases work end to end.

### T6 --- Notifications

-   Implement notification channels and permission handling.
-   Schedule and reconcile reminders.
-   Test app restart, reboot, denied permission, and rescheduled items.

**Deliverable:** Reminders work under normal Android background
restrictions.

### T7 --- Reports and Backup

-   Implement monthly totals, category summaries, and comparisons.
-   Add CSV export and versioned JSON backup/restore.
-   Add PDF export if time allows.
-   Test import/export with sample data.

**Deliverable:** Reports and recoverable records.

### T8 --- Testing and Release

-   Test calculations, recurring schedules, persistence, navigation, and
    data recovery.
-   Verify small-screen layouts and accessibility.
-   Test database upgrades and process termination.
-   Prepare signed release build and installation instructions.

**Deliverable:** Tested MVP ready for personal use.

## 11. Acceptance Criteria

The MVP is ready when: - Income and expenses can be created, edited, and
deleted offline. - Dashboard and reports correctly calculate actual
income, expenses, and net balance. - Milk entries support quantity,
rate, skipped days, and corrections. - Recurring schedules generate no
duplicate occurrences. - Shop rent supports expected amounts, partial
receipts, and outstanding balances. - Expected income and unpaid bills
do not inflate actual financial totals. - Reminder behavior and Android
permission limitations are handled clearly. - JSON backup restores after
validation and CSV exports the selected transactions. - Schedule edits
do not rewrite historical transactions. - Data survives app restarts and
supported database upgrades.

## 12. Effort Estimate

Indicative estimate for one experienced Android developer working
full-time:

  Phase                                     Estimated effort
  --------------------------------------- ------------------
  Project and database foundation                  3--5 days
  Dashboard and transactions                       4--6 days
  Recurrence engine and milk/rent flows            5--8 days
  Notifications and reports                        4--6 days
  Backup, restore, and export                      2--4 days
  Testing, polish, and release                     4--6 days

**Overall planning estimate:** approximately 4--7 weeks full-time. This
is an estimate, not a delivery guarantee. PDF export is the first
feature to defer if the schedule becomes tight.

## 13. Final Recommendation

Build Version 1 around three everyday actions: record spending, record
income, and review the month. Keep the interface simple and make the
data model, financial calculations, recurring schedules, partial rent
receipts, and backup/restore reliable before adding optional features.

The first milestone is **T1: Project Setup and Architecture**, followed
by **T2: Database and Financial Rules**.
