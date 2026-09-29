package com.mobile.data

import android.content.Context
import android.util.Log
import com.mobile.data.db.AppDatabase
import com.mobile.data.db.toDomain
import com.mobile.data.db.toEntity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Persists generated Achievement Certificates (Room-backed) so they can be re-downloaded or re-shared later without regenerating them. */
object CertificateRepository {
    private const val TAG = "CertificateRepository"

    @Volatile
    private var dbInstance: AppDatabase? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Unhandled coroutine exception in CertificateRepository", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private var initialized = false

    private val _certificates = MutableStateFlow<List<Certificate>>(emptyList())
    val certificates: StateFlow<List<Certificate>> = _certificates.asStateFlow()

    fun getDb(context: Context? = null): AppDatabase? {
        return dbInstance ?: synchronized(this) {
            dbInstance ?: context?.let { ctx ->
                try {
                    AppDatabase.getInstance(ctx.applicationContext).also { dbInstance = it }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to obtain AppDatabase instance", t)
                    null
                }
            }
        }
    }

    @Synchronized
    fun init(context: Context) {
        val database = getDb(context) ?: return
        if (initialized) return
        initialized = true
        scope.launch {
            try {
                database.certificateDao().observeAll().collect { entities ->
                    _certificates.value = entities.map { it.toDomain() }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error observing certificates", t)
            }
        }
    }

    fun save(certificate: Certificate) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.certificateDao().insert(certificate.toEntity())
            } catch (t: Throwable) {
                Log.e(TAG, "Error saving certificate ${certificate.id}", t)
            }
        }
    }

    fun delete(certificate: Certificate) {
        scope.launch {
            try {
                val database = getDb() ?: return@launch
                database.certificateDao().delete(certificate.id)
            } catch (t: Throwable) {
                Log.e(TAG, "Error deleting certificate ${certificate.id}", t)
            }
        }
    }
}
