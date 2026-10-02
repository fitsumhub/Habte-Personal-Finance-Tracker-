package com.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.*
import androidx.compose.ui.draw.scale
import com.mobile.data.FinanceRepository
import com.mobile.data.SettingsRepository
import com.mobile.data.SummaryScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast

// BUG FIX: Screen still hardcoded the old dark palette; route through
// MaterialTheme.colorScheme tokens to match the light corporate theme.
private val ExpenseColor = Color(0xFFDC2626)

private fun cacheDirSizeMb(context: android.content.Context): Double {
    fun sizeOf(file: java.io.File): Long =
        if (file.isDirectory) file.listFiles()?.sumOf { sizeOf(it) } ?: 0L else file.length()
    return sizeOf(context.cacheDir) / (1024.0 * 1024.0)
}

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val biometric by SettingsRepository.biometricEnabled.collectAsState()
    val autoHide by SettingsRepository.autoHideBalances.collectAsState()
    val privacyMode by SettingsRepository.privacyMode.collectAsState()
    val notifications by SettingsRepository.notificationsEnabled.collectAsState()
    val smsAlerts by SettingsRepository.smsAlerts.collectAsState()
    val notificationCaptureEnabled by SettingsRepository.notificationCaptureEnabled.collectAsState()
    val dateFormat by SettingsRepository.dateFormat.collectAsState()
    val calendarSystem by SettingsRepository.calendarSystem.collectAsState()
    val theme by SettingsRepository.theme.collectAsState()
    val userName by SettingsRepository.userName.collectAsState()
    val userEmail by SettingsRepository.userEmail.collectAsState()
    val summaryFrequencies by SettingsRepository.summaryFrequencies.collectAsState()
    val adFreeUntilMillis by SettingsRepository.adFreeUntilMillis.collectAsState()
    val isAdFree = System.currentTimeMillis() < adFreeUntilMillis

    var isSyncing by remember { mutableStateOf(false) }
    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showCalendarSystemDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSummaryFrequencyDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    var cacheSize by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        cacheSize = withContext(Dispatchers.IO) { cacheDirSizeMb(context) }
    }

    val transactions by FinanceRepository.transactions.collectAsState()

    // File creation launcher
    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    val writer = java.io.OutputStreamWriter(outputStream)
                    val csv = java.lang.StringBuilder("Date,Title,Amount,Type,Category,Bank\n")
                    transactions.forEach { t ->
                        csv.append("${t.date},${t.title},${t.amount},${t.type},${t.category},${t.bankShortName}\n")
                    }
                    writer.write(csv.toString())
                    writer.flush()
                }
                Toast.makeText(context, "Data exported successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D0A))
            .statusBarsPadding()
    ) {
        // Header
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "Settings",
                color = Color(0xFFD4A017),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 120.dp)
        ) {
            // Profile Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .clickable {
                         haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                         showProfileDialog = true
                    }
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x4DFFFFFF),
                                        Color(0x00FFFFFF)
                                    )
                                )
                            )
                            .border(1.5.dp, Color(0x4DFFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp).align(Alignment.Center)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userEmail,
                            color = Color(0xD9FFFFFF),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Group: Security
            SettingsGroup("SECURITY") {
                SettingToggleRow(
                    icon = Icons.Default.Lock,
                    label = "Biometric Login",
                    description = "Fingerprint or face ID authentication",
                    checked = biometric,
                    onCheckedChange = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SettingsRepository.setBiometric(it) 
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingToggleRow(
                    icon = Icons.Default.Search, // Placeholder for VisibilityOff
                    label = "Auto-hide Balances",
                    description = "Hide amounts when app opens",
                    checked = autoHide,
                    onCheckedChange = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SettingsRepository.setAutoHideBalances(it) 
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingToggleRow(
                    icon = Icons.Default.Build, // Placeholder for Shield
                    label = "Privacy Mode",
                    description = "Enhanced transaction privacy",
                    checked = privacyMode,
                    onCheckedChange = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SettingsRepository.setPrivacyMode(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Group: Notifications
            SettingsGroup("NOTIFICATIONS") {
                SettingToggleRow(
                    icon = Icons.Default.Notifications,
                    label = "Push Notifications",
                    description = "Transaction alerts and updates",
                    checked = notifications,
                    onCheckedChange = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SettingsRepository.setNotifications(it) 
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingToggleRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = "SMS Alerts",
                    description = "Transaction notifications via SMS",
                    checked = smsAlerts,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SettingsRepository.setSmsAlerts(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Summarize,
                    label = "Spending Summary",
                    value = if (summaryFrequencies.isEmpty()) {
                        "Off"
                    } else {
                        summaryFrequencies.sortedBy { SummaryScheduler.ALL_FREQUENCIES.indexOf(it) }.joinToString(", ")
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSummaryFrequencyDialog = true
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.NotificationsActive,
                    label = "Notification Capture",
                    value = if (notificationCaptureEnabled) "On" else "Off",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigate("notification_capture")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Group: Preferences
            SettingsGroup("PREFERENCES") {
                // Not user-selectable: every transaction is parsed directly from Ethiopian
                // bank/telecom SMS in Birr, so a currency picker with no real conversion
                // behind it would just mislabel real amounts. See feedback_settings_functionality.
                SettingOptionRow(
                    icon = Icons.Default.ShoppingCart,
                    label = "Currency",
                    value = "Ethiopian Birr (ETB)",
                    interactive = false
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                // Not user-selectable: the app has no localized strings beyond the app name
                // (see res/values-am) — a language picker with nothing behind it would just
                // silently do nothing when switched.
                SettingOptionRow(
                    icon = Icons.Default.Face,
                    label = "Language",
                    value = "English",
                    interactive = false
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.DateRange,
                    label = "Date Format",
                    value = dateFormat,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDateFormatDialog = true
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.CalendarMonth,
                    label = "Calendar System",
                    value = calendarSystem,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCalendarSystemDialog = true
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Settings,
                    label = "Theme",
                    value = theme,
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showThemeDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Group: Data & Storage
            SettingsGroup("DATA & STORAGE") {
                SettingOptionRow(
                    icon = Icons.Default.Refresh,
                    label = "Manual Data Sync",
                    value = if (isSyncing) "Syncing..." else "Last synced: Just now",
                    onClick = { 
                        if (!isSyncing) {
                            scope.launch {
                                isSyncing = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                FinanceRepository.syncHistoricalSms(context)
                                kotlinx.coroutines.delay(1500)
                                isSyncing = false
                                Toast.makeText(context, "Financial data synchronized!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Delete,
                    label = "Cache Size",
                    value = String.format("%.1f MB", cacheSize),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
                                cacheSize = withContext(Dispatchers.IO) { cacheDirSizeMb(context) }
                                Toast.makeText(context, "Cache cleared successfully!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Share,
                    label = "Export Data",
                    value = "",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (transactions.isEmpty()) {
                            Toast.makeText(context, "No transactions found to export", Toast.LENGTH_SHORT).show()
                        } else {
                            val fileName = "Habte_Export_${System.currentTimeMillis()}.csv"
                            createDocumentLauncher.launch(fileName)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Add,
                    label = "Import Data",
                    value = "",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigate("export_data")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Group: Support — lets a user trade a short rewarded ad for a 24-hour break
            // from banner/interstitial ads, instead of those formats being pure dead code.
            SettingsGroup("SUPPORT") {
                SettingOptionRow(
                    icon = Icons.Default.CardGiftcard,
                    label = if (isAdFree) "Ad-Free Active" else "Go Ad-Free for 24 Hours",
                    value = if (isAdFree) {
                        val remainingMs = (adFreeUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)
                        val hours = remainingMs / (60 * 60 * 1000)
                        val minutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000)
                        "${hours}h ${minutes}m left"
                    } else {
                        "Watch a short ad"
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val activity = context as? android.app.Activity
                        if (activity != null && com.mobile.ads.AdMobService.isRewardedAdReady) {
                            com.mobile.ads.AdMobService.showRewardedIfLoaded(
                                activity,
                                onReward = {
                                    SettingsRepository.grantAdFree(24 * 60 * 60 * 1000L)
                                    Toast.makeText(context, "You're ad-free for the next 24 hours!", Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Ad not ready yet — please try again shortly", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Group: About
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
                } catch (e: Exception) {
                    "—"
                }
            }
            SettingsGroup("ABOUT") {
                SettingOptionRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = versionName,
                    interactive = false
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Terms of Service",
                    value = "",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showTermsDialog = true
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Lock,
                    label = "Privacy Policy",
                    value = "",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showPrivacyDialog = true
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 56.dp))
                SettingOptionRow(
                    icon = Icons.Default.Security,
                    label = "Ad Privacy Choices",
                    value = "",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            com.mobile.ads.AdMobConsent.showPrivacyOptionsForm(activity) { error ->
                                if (error != null) {
                                    Toast.makeText(context, "Consent settings are not required in your region.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Settings is one of the few screens allowed to show a banner ad (see
            // AdMobConfig.BANNER_ALLOWED_ROUTES) — never a financial-action screen.
            Spacer(modifier = Modifier.height(20.dp))
            com.mobile.ads.BannerAdView()
        }
    }

    if (showDateFormatDialog) {
        SelectionDialog(
            title = "Select Date Format",
            options = listOf("MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD"),
            selected = dateFormat,
            onSelect = { SettingsRepository.setDateFormat(it) },
            onDismiss = { showDateFormatDialog = false }
        )
    }

    if (showCalendarSystemDialog) {
        SelectionDialog(
            title = "Select Calendar System",
            options = listOf("Gregorian", "Ethiopian"),
            selected = calendarSystem,
            onSelect = { SettingsRepository.setCalendarSystem(it) },
            onDismiss = { showCalendarSystemDialog = false }
        )
    }

    if (showThemeDialog) {
        SelectionDialog(
            title = "Select Theme",
            options = listOf("Light", "Dark", "Binance Pro", "Addis Gold"),
            selected = theme,
            onSelect = { SettingsRepository.setTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showSummaryFrequencyDialog) {
        MultiSelectionDialog(
            title = "Spending Summary",
            subtitle = "Choose any combination — each fires independently.",
            options = SummaryScheduler.ALL_FREQUENCIES,
            selectedOptions = summaryFrequencies,
            onToggle = { frequency, enabled ->
                val updated = if (enabled) summaryFrequencies + frequency else summaryFrequencies - frequency
                SettingsRepository.setSummaryFrequencies(updated)
                SummaryScheduler.toggle(context, frequency, enabled)
            },
            onDismiss = { showSummaryFrequencyDialog = false }
        )
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            currentName = userName,
            currentEmail = userEmail,
            onSave = { name, email ->
                SettingsRepository.setUserName(name)
                SettingsRepository.setUserEmail(email)
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    if (showTermsDialog) {
        InfoDialog(
            title = "Terms of Service",
            content = "By using this application, you agree to the following terms...\n\n1. Data Privacy: Your financial data is stored locally on your device.\n2. SMS Access: This app requires SMS permission to automatically track your transactions.\n3. Security: You are responsible for maintaining the security of your device and biometric data.",
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showPrivacyDialog) {
        InfoDialog(
            title = "Privacy Policy",
            content = "Your privacy is our priority.\n\n" +
                "- Your SMS messages, transactions, accounts, budgets, payment reminders, and certificates are processed and stored locally on your device only — never uploaded anywhere.\n" +
                "- Notification Capture (Settings > Notification Capture) is an optional, off-by-default alternative to SMS detection — it only ever reads notifications from apps you explicitly choose, and stays just as local as everything else.\n" +
                "- There is no cloud sync and no account/login system.\n" +
                "- The app shows ads via Google AdMob, which may collect an advertising identifier per Google's own policies — but AdMob never receives your SMS or financial data.\n" +
                "- Bank logos are fetched from Google's public favicon service using only the bank's domain name.\n" +
                "- Crash reports are saved locally to help recover the app after a crash and are never transmitted.\n\n" +
                "Full policy: https://fitsumhub.github.io/Habte-Financial-Tracker-App/privacy-policy.html",
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditDialog(
    currentName: String,
    currentEmail: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    // BUG FIX: TextFieldDefaults.outlinedTextFieldColors() is deprecated in
                    // Material3 — replaced with the current OutlinedTextFieldDefaults.colors().
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    // BUG FIX: TextFieldDefaults.outlinedTextFieldColors() is deprecated in
                    // Material3 — replaced with the current OutlinedTextFieldDefaults.colors().
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, email); onDismiss() }) {
                Text("Save Changes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun InfoDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(content, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(Color(0xFFD4A017))
            )
            Text(
                text = title,
                color = Color(0xFFD4A017),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1812))
                .border(1.dp, Color(0x22D4A017), RoundedCornerShape(20.dp))
        ) {
            content()
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: ImageVector? = null,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD4A017),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D0A00),
                checkedTrackColor = Color(0xFFD4A017),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = Color(0xFF2A2218),
                uncheckedBorderColor = Color.Transparent
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun SettingOptionRow(
    icon: ImageVector? = null,
    label: String,
    value: String,
    interactive: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (interactive) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD4A017),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (interactive) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option); onDismiss() },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = option,
                            color = if (option == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectionDialog(
    title: String,
    subtitle: String? = null,
    options: List<String>,
    selectedOptions: Set<String>,
    onToggle: (option: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column {
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                options.forEach { option ->
                    val isChecked = option in selectedOptions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggle(option, !isChecked) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggle(option, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = option,
                            color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

