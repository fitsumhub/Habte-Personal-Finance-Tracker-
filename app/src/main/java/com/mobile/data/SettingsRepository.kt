package com.mobile.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SettingsRepository {
    private const val TAG = "SettingsRepository"
    private const val PREFS_NAME = "habte_settings"

    @Volatile
    private var prefsInstance: SharedPreferences? = null

    private val _biometricEnabled = MutableStateFlow(true)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _autoHideBalances = MutableStateFlow(false)
    val autoHideBalances: StateFlow<Boolean> = _autoHideBalances.asStateFlow()
    
    private val _privacyMode = MutableStateFlow(false)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _smsAlerts = MutableStateFlow(true)
    val smsAlerts: StateFlow<Boolean> = _smsAlerts.asStateFlow()

    private val _dateFormat = MutableStateFlow("MM/DD/YYYY")
    val dateFormat: StateFlow<String> = _dateFormat.asStateFlow()

    // "Gregorian" or "Ethiopian" — which calendar system dates are displayed in across the app.
    private val _calendarSystem = MutableStateFlow("Gregorian")
    val calendarSystem: StateFlow<String> = _calendarSystem.asStateFlow()

    // Default financial overview period used in profile/dashboard reporting.
    private val _financialOverviewPeriod = MutableStateFlow("Monthly")
    val financialOverviewPeriod: StateFlow<String> = _financialOverviewPeriod.asStateFlow()

    private val _theme = MutableStateFlow("Light")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _hasPinSet = MutableStateFlow(false)
    val hasPinSet: StateFlow<Boolean> = _hasPinSet.asStateFlow()

    private val _hasSeenOnboarding = MutableStateFlow(false)
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    // Master switch for NotificationCaptureListenerService
    private val _notificationCaptureEnabled = MutableStateFlow(true)
    val notificationCaptureEnabled: StateFlow<Boolean> = _notificationCaptureEnabled.asStateFlow()

    // Installed bank/wallet apps being monitored, packageName -> InstitutionCatalog id
    private val _monitoredApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val monitoredApps: StateFlow<Map<String, String>> = _monitoredApps.asStateFlow()

    private val _userName = MutableStateFlow("Account Holder")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _profilePhotoUri = MutableStateFlow<String?>(null)
    val profilePhotoUri: StateFlow<String?> = _profilePhotoUri.asStateFlow()

    private val _lastSmsSyncTimestamp = MutableStateFlow(0L)
    val lastSmsSyncTimestamp: StateFlow<Long> = _lastSmsSyncTimestamp.asStateFlow()

    private val _summaryFrequencies = MutableStateFlow(setOf("Daily"))
    val summaryFrequencies: StateFlow<Set<String>> = _summaryFrequencies.asStateFlow()

    private val _adFreeUntilMillis = MutableStateFlow(0L)
    val adFreeUntilMillis: StateFlow<Long> = _adFreeUntilMillis.asStateFlow()

    private val _widgetsEnabled = MutableStateFlow(true)
    val widgetsEnabled: StateFlow<Boolean> = _widgetsEnabled.asStateFlow()

    private val _preferredWidgetStyle = MutableStateFlow("Daily Digest")
    val preferredWidgetStyle: StateFlow<String> = _preferredWidgetStyle.asStateFlow()

    private val _hiddenAccountIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenAccountIds: StateFlow<Set<String>> = _hiddenAccountIds.asStateFlow()

    private val _profileAvatarRing = MutableStateFlow("Emerald")
    val profileAvatarRing: StateFlow<String> = _profileAvatarRing.asStateFlow()

    @Volatile
    private var applicationContext: Context? = null

    fun isAdFreeActive(): Boolean = System.currentTimeMillis() < _adFreeUntilMillis.value

    fun getPrefs(context: Context? = null): SharedPreferences? {
        return prefsInstance ?: synchronized(this) {
            prefsInstance ?: (context ?: applicationContext)?.let { ctx ->
                try {
                    ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                        prefsInstance = it
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error obtaining SharedPreferences instance", t)
                    null
                }
            }
        }
    }

    @Synchronized
    fun init(context: Context) {
        try {
            applicationContext = context.applicationContext
            val prefs = getPrefs(context) ?: return
            _biometricEnabled.value = prefs.getBoolean("biometric", true)
            _autoHideBalances.value = prefs.getBoolean("auto_hide", false)
            _privacyMode.value = prefs.getBoolean("privacy_mode", false)
            _notificationsEnabled.value = prefs.getBoolean("notifications", true)
            _smsAlerts.value = prefs.getBoolean("sms_alerts", true)
            _dateFormat.value = prefs.getString("date_format", "MM/DD/YYYY") ?: "MM/DD/YYYY"
            _calendarSystem.value = prefs.getString("calendar_system", "Gregorian") ?: "Gregorian"
            _financialOverviewPeriod.value = prefs.getString("financial_overview_period", "Monthly") ?: "Monthly"
            _theme.value = prefs.getString("theme", "Light") ?: "Light"
            _hasPinSet.value = prefs.contains("pin_hash")
            _hasSeenOnboarding.value = prefs.getBoolean("has_seen_onboarding", false)
            _notificationCaptureEnabled.value = prefs.getBoolean("notification_capture_enabled", true)
            _monitoredApps.value = prefs.getStringSet("monitored_apps", emptySet())
                ?.mapNotNull { encoded ->
                    val parts = encoded.split("::", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                ?.toMap() ?: emptyMap()
            _userName.value = prefs.getString("user_name", "Account Holder") ?: "Account Holder"
            _userEmail.value = prefs.getString("user_email", "") ?: ""
            _profilePhotoUri.value = prefs.getString("profile_photo_uri", null)
            _lastSmsSyncTimestamp.value = prefs.getLong("last_sms_sync_timestamp", 0L)
            _summaryFrequencies.value = prefs.getStringSet("summary_frequencies", setOf("Daily"))?.toSet() ?: setOf("Daily")
            _adFreeUntilMillis.value = prefs.getLong("ad_free_until", 0L)
            _widgetsEnabled.value = prefs.getBoolean("widgets_enabled", true)
            _preferredWidgetStyle.value = prefs.getString("preferred_widget_style", "Daily Digest") ?: "Daily Digest"
            _hiddenAccountIds.value = prefs.getStringSet("hidden_account_ids", emptySet())?.toSet() ?: emptySet()
            _profileAvatarRing.value = prefs.getString("profile_avatar_ring", "Emerald") ?: "Emerald"
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing settings", t)
        }
    }

    private fun safeEdit(action: SharedPreferences.Editor.() -> Unit) {
        try {
            val prefs = getPrefs(applicationContext) ?: return
            val editor = prefs.edit()
            editor.action()
            editor.apply()
        } catch (t: Throwable) {
            Log.e(TAG, "Error saving preference", t)
        }
    }

    fun setProfileAvatarRing(ring: String) {
        _profileAvatarRing.value = ring
        safeEdit { putString("profile_avatar_ring", ring) }
    }

    fun setWidgetsEnabled(enabled: Boolean) {
        _widgetsEnabled.value = enabled
        safeEdit { putBoolean("widgets_enabled", enabled) }
        applicationContext?.let {
            try {
                WeeklySpendingWidgetUpdater.updateAllWidgets(it)
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating widgets on widgetsEnabled toggle", t)
            }
        }
    }

    fun setPreferredWidgetStyle(style: String) {
        _preferredWidgetStyle.value = style
        safeEdit { putString("preferred_widget_style", style) }
        applicationContext?.let {
            try {
                WeeklySpendingWidgetUpdater.updateAllWidgets(it)
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating widgets on preferred style change", t)
            }
        }
    }

    fun toggleAccountVisibility(accountId: String) {
        val current = _hiddenAccountIds.value.toMutableSet()
        if (current.contains(accountId)) {
            current.remove(accountId)
        } else {
            current.add(accountId)
        }
        _hiddenAccountIds.value = current
        safeEdit { putStringSet("hidden_account_ids", current) }
        applicationContext?.let {
            try {
                WeeklySpendingWidgetUpdater.updateAllWidgets(it)
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating widgets on account visibility toggle", t)
            }
        }
    }

    fun isAccountHidden(accountId: String): Boolean = _hiddenAccountIds.value.contains(accountId)

    fun setProfilePhotoUri(uri: String?) {
        _profilePhotoUri.value = uri
        safeEdit { putString("profile_photo_uri", uri) }
    }

    fun setLastSmsSyncTimestamp(timestamp: Long) {
        _lastSmsSyncTimestamp.value = timestamp
        safeEdit { putLong("last_sms_sync_timestamp", timestamp) }
    }

    fun setBiometric(enabled: Boolean) {
        _biometricEnabled.value = enabled
        safeEdit { putBoolean("biometric", enabled) }
    }

    fun setAutoHideBalances(enabled: Boolean) {
        _autoHideBalances.value = enabled
        safeEdit { putBoolean("auto_hide", enabled) }
    }
    
    fun setPrivacyMode(enabled: Boolean) {
        _privacyMode.value = enabled
        safeEdit { putBoolean("privacy_mode", enabled) }
    }

    fun setNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        safeEdit { putBoolean("notifications", enabled) }
        applicationContext?.let {
            try {
                if (enabled) {
                    SummaryScheduler.rescheduleAll(it, _summaryFrequencies.value)
                } else {
                    SummaryScheduler.rescheduleAll(it, emptySet())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating summary scheduler on notification toggle", t)
            }
        }
    }

    fun setSmsAlerts(enabled: Boolean) {
        _smsAlerts.value = enabled
        safeEdit { putBoolean("sms_alerts", enabled) }
    }

    fun setDateFormat(value: String) {
        _dateFormat.value = value
        safeEdit { putString("date_format", value) }
    }

    fun setCalendarSystem(value: String) {
        _calendarSystem.value = value
        safeEdit { putString("calendar_system", value) }
    }

    fun setFinancialOverviewPeriod(value: String) {
        _financialOverviewPeriod.value = value
        safeEdit { putString("financial_overview_period", value) }
    }

    fun setTheme(value: String) {
        _theme.value = value
        safeEdit { putString("theme", value) }
    }

    fun setUserName(value: String) {
        _userName.value = value
        safeEdit { putString("user_name", value) }
    }

    fun setUserEmail(value: String) {
        _userEmail.value = value
        safeEdit { putString("user_email", value) }
    }

    fun setSummaryFrequencies(values: Set<String>) {
        _summaryFrequencies.value = values
        safeEdit { putStringSet("summary_frequencies", HashSet(values)) }
        applicationContext?.let {
            try {
                if (_notificationsEnabled.value) {
                    SummaryScheduler.rescheduleAll(it, values)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating summary scheduler on frequency change", t)
            }
        }
    }

    /** Grants an ad-free window of [durationMillis] from now, extending any grant already in progress. */
    fun grantAdFree(durationMillis: Long) {
        val until = (System.currentTimeMillis() + durationMillis).coerceAtLeast(_adFreeUntilMillis.value)
        _adFreeUntilMillis.value = until
        safeEdit { putLong("ad_free_until", until) }
    }

    fun setHasSeenOnboarding(seen: Boolean) {
        _hasSeenOnboarding.value = seen
        safeEdit { putBoolean("has_seen_onboarding", seen) }
    }

    fun setNotificationCaptureEnabled(enabled: Boolean) {
        _notificationCaptureEnabled.value = enabled
        safeEdit { putBoolean("notification_capture_enabled", enabled) }
    }

    fun setMonitoredApps(apps: Map<String, String>) {
        _monitoredApps.value = apps
        val encoded = apps.map { (packageName, institutionId) -> "$packageName::$institutionId" }.toHashSet()
        safeEdit { putStringSet("monitored_apps", encoded) }
    }

    fun autoEnableDetectedApps(matched: List<MonitorableApp>) {
        if (matched.isEmpty()) return
        val current = _monitoredApps.value
        val missing = matched.filter { it.packageName !in current }
        if (missing.isEmpty()) return
        val updated = current.toMutableMap()
        missing.forEach { updated[it.packageName] = it.institution.id }
        setMonitoredApps(updated)
    }

    fun setAppPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        safeEdit {
            putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            putString("pin_hash", hashPin(pin, salt))
        }
        _hasPinSet.value = true
    }

    /** Returns true if [pin] matches the stored PIN, or if no PIN has been set yet. */
    fun verifyPin(pin: String): Boolean {
        if (!_hasPinSet.value) return true
        val prefs = getPrefs() ?: return false
        val saltStr = prefs.getString("pin_salt", null) ?: return false
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        return hashPin(pin, salt) == storedHash
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun clearAll(context: Context) {
        _userName.value = "Account Holder"
        _userEmail.value = ""
        _profilePhotoUri.value = null
        _lastSmsSyncTimestamp.value = 0L
        _theme.value = "Light"
        _calendarSystem.value = "Gregorian"
        _dateFormat.value = "MM/DD/YYYY"
        _financialOverviewPeriod.value = "Monthly"
        _notificationsEnabled.value = true
        _smsAlerts.value = true
        _notificationCaptureEnabled.value = true
        _summaryFrequencies.value = setOf("Daily")
        _biometricEnabled.value = true
        _autoHideBalances.value = false
        _privacyMode.value = false
        _hasPinSet.value = false
        _hasSeenOnboarding.value = false
        _monitoredApps.value = emptyMap()
        _adFreeUntilMillis.value = 0L

        safeEdit { clear() }
    }
}
