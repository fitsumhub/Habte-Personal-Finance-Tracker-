package com.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, BankEntity::class, AccountEntity::class, BudgetEntity::class, PaymentReminderEntity::class, CertificateEntity::class, DeletedTransactionEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun bankDao(): BankDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun paymentReminderDao(): PaymentReminderDao
    abstract fun certificateDao(): CertificateDao
    abstract fun deletedTransactionDao(): DeletedTransactionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // Additive-only — creates the payment_reminders table without touching any
        // existing rows, so upgrading never loses a user's synced transactions/accounts.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        category TEXT NOT NULL,
                        amount REAL NOT NULL,
                        payee TEXT NOT NULL,
                        dueDateMillis INTEGER NOT NULL,
                        repeat TEXT NOT NULL,
                        daysBefore INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        lastPaidCycle TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Additive-only, same as MIGRATION_1_2 — creates the certificates table (Achievement
        // Certificates feature) without touching any existing rows.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS certificates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        period TEXT NOT NULL,
                        periodLabel TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        photoPath TEXT,
                        achievementTitle TEXT NOT NULL,
                        achievementSubtitle TEXT NOT NULL,
                        generatedAtMillis INTEGER NOT NULL,
                        imagePath TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Additive-only — a tombstone table so a transaction the user deliberately deleted
        // (via Duplicate Check) can never be silently re-inserted by a later SMS re-sync.
        // Without this, syncHistoricalSms re-parses the *entire* SMS inbox every time it
        // runs (the original SMS never leaves the phone's inbox just because the derived
        // transaction was deleted) and insertIgnoringExisting only skips a row if one with
        // the same id *already exists* — once deleted, that id is free again, so the exact
        // "duplicate" the user just removed would silently come back on the next sync.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deleted_transactions (
                        transactionId TEXT PRIMARY KEY NOT NULL,
                        deletedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Deduplicate accounts table to prevent unique constraint failures
                db.execSQL(
                    """
                    DELETE FROM accounts 
                    WHERE id NOT IN (
                        SELECT id 
                        FROM accounts 
                        GROUP BY bankId, accountNumber 
                        HAVING id = MIN(id)
                    )
                    """.trimIndent()
                )
                // Add the unique index
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_bankId_accountNumber` ON `accounts` (`bankId`, `accountNumber`)")
            }
        }

        // Migration 5→6: introduces the canonicalKey deduplication column.
        //
        // Steps (all in one SQLite transaction):
        //   1. Add canonicalKey TEXT column (NOT NULL with default '').
        //   2. Backfill canonicalKey for every existing account row using the account's
        //      bankShortName + last-4-digits of accountNumber (or ':unknown' if none).
        //   3. Find every (bankId, canonicalKey=':unknown') placeholder that has a
        //      corresponding suffixed row for the same bank — those pairs are duplicates.
        //      Reassign the placeholder's id to the suffixed row, then delete the placeholder.
        //      (Transactions reference bankShortName+accountSuffix directly, not accountId,
        //      so no transaction reassignment is needed.)
        //   4. Add UNIQUE INDEX on (bankId, canonicalKey).
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add canonicalKey column
                db.execSQL("ALTER TABLE accounts ADD COLUMN canonicalKey TEXT NOT NULL DEFAULT ''")

                // 2. Backfill: extract last 4 digits from accountNumber.
                //    accountNumber formats: "•••• 6936", "Unknown", ""
                //    SUBSTR + LTRIM trick: strip non-digits by taking last chunk after spaces/bullets.
                //    Simplest portable approach: use CASE on accountNumber content.
                db.execSQL("""
                    UPDATE accounts SET canonicalKey = (
                        SELECT banks.shortName || ':' ||
                            CASE
                                WHEN accounts.accountNumber = 'Unknown' OR accounts.accountNumber = '' THEN 'unknown'
                                ELSE SUBSTR(REPLACE(REPLACE(REPLACE(accounts.accountNumber,'•',''),' ',''),'*',''), -4)
                            END
                        FROM banks WHERE banks.id = accounts.bankId
                    )
                """.trimIndent())

                // 3. Merge duplicates: for every Unknown placeholder that shares a bankId with
                //    a suffixed account, the Unknown is the orphan — delete it.
                //    Transactions are unaffected because they use (bankShortName, accountSuffix)
                //    columns directly, not the account row's primary key.
                db.execSQL("""
                    DELETE FROM accounts
                    WHERE canonicalKey LIKE '%:unknown'
                    AND bankId IN (
                        SELECT DISTINCT bankId FROM accounts
                        WHERE canonicalKey NOT LIKE '%:unknown' AND canonicalKey != ''
                    )
                """.trimIndent())

                // 3b. Guarantee no duplicates exist on (bankId, canonicalKey) before creating the unique index
                db.execSQL("""
                    DELETE FROM accounts
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM accounts GROUP BY bankId, canonicalKey
                    )
                """.trimIndent())

                // 4. Add UNIQUE INDEX on (bankId, canonicalKey)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_bankId_canonicalKey` ON `accounts` (`bankId`, `canonicalKey`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habte.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
            }
    }
}
