package com.mobile.data

import android.content.Context
import android.net.Uri
import com.mobile.data.db.AccountEntity
import com.mobile.data.db.AppDatabase
import com.mobile.data.db.toDomain
import com.mobile.data.db.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

object FinanceRepository {

    @Volatile
    private var dbInstance: AppDatabase? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("FinanceRepository", "Unhandled coroutine exception in FinanceRepository", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private var initialized = false

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _banks = MutableStateFlow<List<Bank>>(emptyList())
    val banks: StateFlow<List<Bank>> = _banks.asStateFlow()

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    fun getDb(context: Context? = null): AppDatabase? {
        return dbInstance ?: synchronized(this) {
            dbInstance ?: context?.let { ctx ->
                try {
                    AppDatabase.getInstance(ctx.applicationContext).also { dbInstance = it }
                } catch (t: Throwable) {
                    Log.e("FinanceRepository", "Failed to obtain AppDatabase instance", t)
                    null
                }
            }
        }
    }

    // Set when a transaction notification is tapped, so HomeScreen can jump
    // straight to that transaction's categorize sheet once it's available, then cleared
    // by the observer after it's been consumed.
    private val _pendingTransactionId = MutableStateFlow<String?>(null)
    val pendingTransactionId: StateFlow<String?> = _pendingTransactionId.asStateFlow()

    fun setPendingTransaction(id: String?) {
        _pendingTransactionId.value = id
    }

    // Opens the on-device database and mirrors it into the StateFlows every screen
    // already observes, so transactions/banks/budgets survive process death and app
    // restarts instead of living only in memory. Safe to call more than once — only
    // the first call (from MainActivity.onCreate) takes effect.
    @Synchronized
    fun init(context: Context) {
        val database = getDb(context) ?: return
        if (initialized) return
        initialized = true
        scope.launch {
            try {
                database.transactionDao().observeAll()
                    .map { entities -> entities.map { it.toDomain() } }
                    .distinctUntilChanged()
                    .collect {
                        _transactions.value = it
                        try {
                            WeeklySpendingWidgetUpdater.updateAllWidgets(context.applicationContext)
                        } catch (t: Throwable) {
                            Log.e("FinanceRepository", "Error updating widgets on transaction update", t)
                        }
                    }
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Error observing transactions", t)
            }
        }
        scope.launch {
            try {
                database.bankDao().observeAllWithAccounts()
                    .map { rows -> rows.map { it.toDomain() } }
                    .distinctUntilChanged()
                    .collect { _banks.value = it }
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Error observing banks", t)
            }
        }
        scope.launch {
            try {
                database.budgetDao().observeAll()
                    .map { entities -> entities.map { it.toDomain() } }
                    .distinctUntilChanged()
                    .collect {
                        _budgets.value = it
                        try {
                            WeeklySpendingWidgetUpdater.updateAllWidgets(context.applicationContext)
                        } catch (t: Throwable) {
                            Log.e("FinanceRepository", "Error updating widgets on budget update", t)
                        }
                    }
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Error observing budgets", t)
            }
        }
        scope.launch {
            try {
                ensureDefaultPresetsIfEmpty()
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Error ensuring default presets", t)
            }
        }
    }

    suspend fun ensureDefaultPresetsIfEmpty() = withContext(Dispatchers.IO) {
        val database = getDb() ?: return@withContext
        try {
            val existingBanks = database.bankDao().getAll()
            if (existingBanks.isEmpty()) {
                val defaultInstitutions = listOf("cbe", "tele", "boa", "awash")
                defaultInstitutions.forEach { id ->
                    val preset = Data.PRESET_BANKS.find { it.id == id }
                    if (preset != null) {
                        database.bankDao().insert(preset.toEntity())
                        val canonicalKey = AccountDeduplicator.canonicalKey(preset.shortName, null)
                        val defaultAccount = Account(
                            id = "acc_${preset.id}_main",
                            bankId = preset.id,
                            accountNumber = "•••• 1000",
                            label = "${preset.shortName} Savings Account",
                            balance = 0.0,
                            currency = "ETB",
                            type = if (preset.id == "tele") AccountType.MOBILE_WALLET else AccountType.SAVINGS
                        )
                        database.accountDao().insert(defaultAccount.toEntity(preset.id, canonicalKey))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("FinanceRepository", "Error ensuring default presets", t)
        }
    }

    fun addBank(bank: Bank) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.bankDao().insert(bank.toEntity())
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to add bank", t)
            }
        }
    }

    fun removeBank(bankId: String) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                val shortName = _banks.value.find { it.id == bankId }?.shortName
                database.bankDao().deleteById(bankId)
                if (shortName != null) database.transactionDao().deleteByBank(shortName)
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to remove bank", t)
            }
        }
    }

    fun updateBankColors(bankId: String, colorFrom: String, colorTo: String) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.bankDao().updateColors(bankId, colorFrom, colorTo)
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to update bank colors", t)
            }
        }
    }

    fun updateAccountNumber(accountId: String, accountNumber: String) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.accountDao().updateAccountNumber(accountId, accountNumber)
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to update account number", t)
            }
        }
    }

    fun normalizeAccountIdentifier(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "Unknown"
        val normalized = value.replace(Regex("[^0-9]"), "")
        if (normalized.isEmpty()) {
            return if (value.equals("unknown", ignoreCase = true) || value.equals("main", ignoreCase = true)) {
                "Unknown"
            } else {
                value.trim()
            }
        }

        val digits = if (normalized.length >= 12 && normalized.startsWith("251")) {
            "0${normalized.drop(3)}"
        } else if (normalized.length == 9) {
            "0$normalized"
        } else {
            normalized
        }

        return digits
    }

    fun matchAccounts(a: String?, b: String?): Boolean {
        val leftRaw = a.orEmpty()
        val rightRaw = b.orEmpty()
        val left = normalizeAccountIdentifier(leftRaw)
        val right = normalizeAccountIdentifier(rightRaw)

        if (left == "Unknown" || right == "Unknown") {
            return left == "Unknown" && right == "Unknown"
        }

        if (left == right) return true

        val leftMasked = leftRaw.contains("•") || leftRaw.contains("●") || leftRaw.contains("*") || left.length <= 4
        val rightMasked = rightRaw.contains("•") || rightRaw.contains("●") || rightRaw.contains("*") || right.length <= 4

        if (leftMasked || rightMasked) {
            val leftSuffix = left.takeLast(4)
            val rightSuffix = right.takeLast(4)
            return leftSuffix == rightSuffix
        }

        return false
    }

    // Proper sign-out clears ALL persisted data, not just the in-memory view of it.
    fun clearAll() {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.transactionDao().deleteAll()
                database.accountDao().deleteAll()
                database.bankDao().deleteAll()
                database.budgetDao().deleteAll()
                SettingsRepository.setLastSmsSyncTimestamp(0L)
                ensureDefaultPresetsIfEmpty()
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to clear all data", t)
            }
        }
    }

    fun addTransaction(transaction: Transaction) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                // A transaction id is a deterministic hash of the SMS content (see
                // stableTransactionKey), so a genuinely resent SMS reproduces the exact same id —
                // if the user already deleted it as a duplicate, don't let it come back.
                val deletedIds = database.deletedTransactionDao().getAllIds()
                if (deletedIds.contains(transaction.id)) return@launch
                val account = ensureBankAndAccount(transaction)
                if (account != null && transaction.balance != null) {
                    database.accountDao().updateBalance(account.id, transaction.balance)
                }
                database.transactionDao().upsert(transaction.toEntity())
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to add transaction", t)
            }
        }
    }

    // Used by the Duplicate Check tool to remove a resent SMS that slipped past the
    // id-based insert-ignore dedupe (see stableTransactionKey in SmsParser). Also records a
    // tombstone (see MIGRATION_3_4) so the *same* SMS re-appearing on a future syncHistoricalSms
    // pass — the original message never leaves the phone's SMS inbox — can't silently undo
    // this delete by re-inserting the identical id.
    fun deleteTransaction(transactionId: String) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.transactionDao().deleteById(transactionId)
                database.deletedTransactionDao().markDeleted(
                    com.mobile.data.db.DeletedTransactionEntity(transactionId, System.currentTimeMillis())
                )
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to delete transaction", t)
            }
        }
    }

    fun updateTransactionCategory(transactionId: String, newCategory: String) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.transactionDao().updateCategory(transactionId, newCategory)
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to update transaction category", t)
            }
        }
    }

    // Reason is its own field, independent of category — saving a note never
    // overwrites whichever category chip was selected, and vice versa.
    fun updateTransactionReason(transactionId: String, newReason: String) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.transactionDao().updateReason(transactionId, newReason)
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to update transaction reason", t)
            }
        }
    }

    fun setBudget(period: String, category: String?, limit: Double) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                val existing = _budgets.value.find { it.period == period && it.category == category }
                database.budgetDao().upsert(
                    Budget(id = existing?.id ?: 0, period = period, category = category, limit = limit).toEntity()
                )
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to set budget", t)
            }
        }
    }

    fun deleteBudget(id: Long) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.budgetDao().delete(id)
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Failed to delete budget", t)
            }
        }
    }

    // Creates the Bank/Account rows for a transaction's institution if they don't
    // already exist. Uses a stable canonicalKey (bankCode:last4 or bankCode:unknown) as the
    // single deduplication key — this prevents the "CBE Main Account" + "CBE Account (*6936)"
    // duplication that occurred when the same physical account was observed across multiple
    // SMS messages with different levels of account-number visibility.
    //
    // The three resolution paths in order of priority:
    //   1. Canonical key match    → return the existing account directly.
    //   2. Suffix arriving for    → upgrade the existing Unknown placeholder in place,
    //      an Unknown placeholder   regardless of how many OTHER suffixed accounts exist.
    //   3. No existing match      → create a new account with the canonical key set.
    private suspend fun ensureBankAndAccount(tx: Transaction): AccountEntity? {
        val database = getDb() ?: return null
        return try {
            val bankMetadata = Data.PRESET_BANKS.find { it.shortName == tx.bankShortName } ?: return null
            val institution = InstitutionCatalog.ALL.find { it.shortName == tx.bankShortName }
            val defaultAccountType = if (institution?.type == InstitutionType.DIGITAL_WALLET) {
                AccountType.MOBILE_WALLET
            } else {
                AccountType.SAVINGS
            }

            var bankEntity = database.bankDao().findByShortName(tx.bankShortName)
            if (bankEntity == null) {
                bankEntity = bankMetadata.copy(id = "${tx.bankShortName.lowercase()}_auto").toEntity()
                database.bankDao().insert(bankEntity)
            }

            val incomingKey = AccountDeduplicator.canonicalKey(tx.bankShortName, tx.accountSuffix)

            // ── Path 1: canonical key already known ──────────────────────────────
            val exactMatch = database.accountDao().findByCanonicalKey(bankEntity.id, incomingKey)
            if (exactMatch != null) return exactMatch

            // ── Path 2: incoming SMS has a real suffix, but only an Unknown        ──
            //    placeholder exists for this bank. Upgrade it instead of duplicating.
            if (tx.accountSuffix != null) {
                val unknownKey = AccountDeduplicator.canonicalKey(tx.bankShortName, null)
                val placeholder = database.accountDao().findByCanonicalKey(bankEntity.id, unknownKey)
                if (placeholder != null) {
                    // Ensure no account with incomingKey already exists before upgrading the placeholder
                    val existingAccount = database.accountDao().findByCanonicalKey(bankEntity.id, incomingKey)
                    if (existingAccount != null) return existingAccount

                    val newAccountNumber = "•••• ${tx.accountSuffix}"
                    val newLabel = "${tx.bankShortName} Account (*${tx.accountSuffix})"
                    database.accountDao().upgradeToSuffixed(
                        id = placeholder.id,
                        accountNumber = newAccountNumber,
                        label = newLabel,
                        canonicalKey = incomingKey
                    )
                    return placeholder.copy(
                        accountNumber = newAccountNumber,
                        label = newLabel,
                        canonicalKey = incomingKey
                    )
                }
            }

            // ── Path 3: genuinely new account ────────────────────────────────────
            val newAccount = Account(
                id = "acc_${tx.bankShortName}_${incomingKey.replace(":", "_")}_${System.currentTimeMillis()}",
                bankId = bankEntity.id,
                accountNumber = if (tx.accountSuffix != null) "•••• ${tx.accountSuffix}" else "Unknown",
                label = if (tx.accountSuffix != null) "${tx.bankShortName} Account (*${tx.accountSuffix})" else "${tx.bankShortName} Main Account",
                balance = tx.balance ?: 0.0,
                currency = "ETB",
                type = defaultAccountType
            )
            val entity = newAccount.toEntity(bankEntity.id, canonicalKey = incomingKey)
            database.accountDao().insert(entity)
            entity
        } catch (t: Throwable) {
            Log.e("FinanceRepository", "Error ensuring bank/account for tx: ${tx.id}", t)
            null
        }
    }

data class SyncResult(
    val smsScanned: Int,
    val transactionsParsed: Int,
    val newTransactionsAdded: Int,
    val bankCount: Int
)

    // Runs on Dispatchers.IO to prevent ANR on main thread. Performs ultra-fast
    // incremental syncing by checking messages since last sync timestamp, or full inbox scan
    // on initial startup / manual full resync.
    suspend fun syncHistoricalSms(context: Context, fullResync: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val database = getDb(context)
        val lastSync = if (fullResync) 0L else SettingsRepository.lastSmsSyncTimestamp.value
        val selection = if (lastSync > 0L) "date > ?" else null
        // 60-second safety window to guarantee boundary SMS messages are never dropped
        val selectionArgs = if (lastSync > 0L) arrayOf((lastSync - 60_000L).coerceAtLeast(0L).toString()) else null

        val cursor = runCatching {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                selection,
                selectionArgs,
                "date DESC"
            )
        }.getOrNull()

        val newTransactions = mutableListOf<Transaction>()
        var smsScannedCount = 0

        try {
            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                if (addressIndex >= 0 && bodyIndex >= 0 && dateIndex >= 0) {
                    while (it.moveToNext()) {
                        smsScannedCount++
                        val address = it.getString(addressIndex) ?: continue
                        val body = it.getString(bodyIndex) ?: continue
                        val date = it.getLong(dateIndex)

                        val parsed = SmsParser.parseMessage(address, body, date)
                        if (parsed != null) {
                            newTransactions.add(parsed)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("FinanceRepository", "Error reading SMS cursor in syncHistoricalSms", t)
        }

        SettingsRepository.setLastSmsSyncTimestamp(System.currentTimeMillis())

        var addedCount = 0
        if (newTransactions.isNotEmpty() && database != null) {
            try {
                val deletedIds = database.deletedTransactionDao().getAllIds().toSet()
                val toInsert = if (deletedIds.isEmpty()) newTransactions else newTransactions.filter { it.id !in deletedIds }
                addedCount = toInsert.size
                if (toInsert.isNotEmpty()) {
                    val calibratedAccounts = mutableSetOf<String>()
                    toInsert.forEach { tx ->
                        val account = ensureBankAndAccount(tx)
                        if (account != null && tx.balance != null && calibratedAccounts.add(account.id)) {
                            database.accountDao().updateBalance(account.id, tx.balance)
                        }
                    }
                    toInsert.chunked(250).forEach { chunk ->
                        database.transactionDao().insertIgnoringExisting(chunk.map { it.toEntity() })
                    }
                }
            } catch (t: Throwable) {
                Log.e("FinanceRepository", "Error writing synced transactions to DB", t)
            }
        }

        ensureDefaultPresetsIfEmpty()
        try {
            WeeklySpendingWidgetUpdater.updateAllWidgets(context.applicationContext)
        } catch (t: Throwable) {
            Log.e("FinanceRepository", "Error updating widgets after sync", t)
        }

        val totalBanksCount = if (database != null) {
            try { database.bankDao().getAll().size } catch (t: Throwable) { 0 }
        } else 0
        return@withContext SyncResult(
            smsScanned = smsScannedCount,
            transactionsParsed = newTransactions.size,
            newTransactionsAdded = addedCount,
            bankCount = totalBanksCount
        )
    }

    // Restores transactions from a previously exported JSON backup. Like the SMS sync
    // path, existing rows are left untouched (insert-ignore) so restoring a backup can
    // never overwrite edits made since the backup was taken.
    suspend fun restoreTransactions(items: List<Transaction>) = withContext(Dispatchers.IO) {
        val database = getDb()
        if (items.isEmpty() || database == null) return@withContext
        try {
            items.forEach { ensureBankAndAccount(it) }
            items.chunked(250).forEach { chunk ->
                database.transactionDao().insertIgnoringExisting(chunk.map { it.toEntity() })
            }
        } catch (t: Throwable) {
            Log.e("FinanceRepository", "Error restoring transactions", t)
        }
    }
}

