package com.mobile

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mobile.ads.AdMobService
import com.mobile.data.CertificateRepository
import com.mobile.data.CrashReporter
import com.mobile.data.FinanceRepository
import com.mobile.data.PaymentReminderRepository
import com.mobile.data.PersistedCrash
import com.mobile.data.SettingsRepository
import com.mobile.data.SmsObserver
import com.mobile.data.SummaryScheduler
import com.mobile.data.TransactionNotifier
import com.mobile.ui.RootLayout
import com.mobile.ui.components.ErrorFallback
import com.mobile.ui.screens.OnboardingScreen

private enum class TopLevelScreen { CRASH, LOCKED, ONBOARDING, APP }

class MainActivity : AppCompatActivity() {

    private fun handleNotificationTap(intent: Intent?) {
        val transactionId = intent?.getStringExtra(TransactionNotifier.EXTRA_TRANSACTION_ID) ?: return
        FinanceRepository.setPendingTransaction(transactionId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationTap(intent)
    }

    override fun onResume() {
        super.onResume()
        SmsObserver.register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        SmsObserver.unregister(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Installed before anything else can run, so nothing that follows in this
        // method is a gap in coverage.
        CrashReporter.install(this)
        enableEdgeToEdge()
        SettingsRepository.init(this)
        FinanceRepository.init(this)
        PaymentReminderRepository.init(this)
        CertificateRepository.init(this)
        SmsObserver.register(this)
        // App start — see AdMobService for what this actually does (SDK init + preloading
        // the one-shot ad formats).
        AdMobService.initialize(this)
        handleNotificationTap(intent)
        SummaryScheduler.ensureScheduled(this, SettingsRepository.summaryFrequencies.value)

        // If the previous launch crashed, show the fallback instead of resuming as if
        // nothing happened — see CrashReporter for why this can't be caught live.
        val lastCrash = CrashReporter.consumeLastCrash(this)

        setContent {
            var crash by remember { mutableStateOf(lastCrash?.let { PersistedCrash(it) }) }
            val activeCrash = crash

            val biometricEnabled by SettingsRepository.biometricEnabled.collectAsState()
            val hasPinSet by SettingsRepository.hasPinSet.collectAsState()
            // BUG FIX: The App PIN previously did nothing on launch — only biometrics
            // gated entry, and even that silently bypassed when no biometric was
            // enrolled. Now either protection method (if enabled) actually locks the app.
            val lockEnabled = biometricEnabled || hasPinSet

            var isAuthenticated by remember { mutableStateOf(!lockEnabled) }
            var authFailed by remember { mutableStateOf(false) }
            var showPinEntry by remember { mutableStateOf(false) }
            val privacyMode by SettingsRepository.privacyMode.collectAsState()
            val hasSeenOnboarding by SettingsRepository.hasSeenOnboarding.collectAsState()

            // Update Privacy Mode (Screenshot Protection) - always enforce when app is locked
            LaunchedEffect(privacyMode, isAuthenticated) {
                if (privacyMode || !isAuthenticated) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            fun runBiometricAuth() {
                val biometricManager = BiometricManager.from(this@MainActivity)
                val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    showPinEntry = false
                    showBiometricPrompt { authenticated ->
                        isAuthenticated = authenticated
                        authFailed = !authenticated
                    }
                } else if (hasPinSet) {
                    // No biometric hardware/enrollment — fall back to the PIN instead
                    // of bypassing the lock entirely.
                    showPinEntry = true
                } else {
                    // Neither biometric nor a PIN is actually usable — don't lock the
                    // user out of their own app.
                    isAuthenticated = true
                }
            }

            // Trigger auth on launch if either lock method is enabled — skipped while a
            // crash fallback is showing, so a biometric prompt never fires underneath it.
            LaunchedEffect(activeCrash, lockEnabled) {
                if (activeCrash == null && lockEnabled) {
                    if (biometricEnabled) runBiometricAuth() else showPinEntry = true
                }
            }

            val screenState = when {
                activeCrash != null -> TopLevelScreen.CRASH
                !isAuthenticated -> TopLevelScreen.LOCKED
                !hasSeenOnboarding -> TopLevelScreen.ONBOARDING
                else -> TopLevelScreen.APP
            }

            // AdMob App Open Ad: Triggered when user enters authenticated main app
            // or brings app to foreground, never interrupting PIN lock, onboarding, or crash fallback.
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, screenState) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_START && screenState == TopLevelScreen.APP) {
                        AdMobService.showAppOpenIfAvailable(this@MainActivity)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(screenState) {
                if (screenState == TopLevelScreen.APP) {
                    AdMobService.showAppOpenIfAvailable(this@MainActivity)
                }
            }

            // Crossfade instead of an instant swap — the onboarding screen's dark bespoke
            // palette handing off to the app's light theme (or the lock screen resolving
            // into either) used to snap in one frame; this makes every top-level state
            // change read as one continuous transition instead of a jump-cut.
            Crossfade(targetState = screenState, animationSpec = tween(450), label = "topLevelScreen") { state ->
                when (state) {
                    TopLevelScreen.CRASH -> {
                        // Reads the live `activeCrash`, not a frozen snapshot — Crossfade keeps
                        // this branch composed while it fades out, so if resetError just set
                        // crash to null, this recomposes with activeCrash == null before the
                        // fade finishes. Render nothing for that last frame instead of `!!`-crashing.
                        val crashToShow = activeCrash
                        if (crashToShow != null) {
                            com.mobile.ui.theme.AppTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    ErrorFallback(error = crashToShow, resetError = { crash = null })
                                }
                            }
                        }
                    }
                    TopLevelScreen.ONBOARDING -> {
                        OnboardingScreen(onFinished = { SettingsRepository.setHasSeenOnboarding(true) })
                    }
                    TopLevelScreen.APP -> {
                        RootLayout()
                    }
                    TopLevelScreen.LOCKED -> {
                        com.mobile.ui.theme.AppTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                if (showPinEntry) {
                                    PinLockScreen(
                                        onUnlocked = { isAuthenticated = true },
                                        onUseBiometricInstead = if (biometricEnabled) {
                                            { authFailed = false; runBiometricAuth() }
                                        } else null
                                    )
                                } else {
                                    // Show a simple lock screen while authenticating, with a retry
                                    // path so a cancelled/failed prompt doesn't strand the user.
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = if (authFailed) "Authentication failed" else "App Locked",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Button(onClick = {
                                                authFailed = false
                                                runBiometricAuth()
                                            }) {
                                                Text("Try Again")
                                            }
                                            if (hasPinSet) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                TextButton(onClick = { showPinEntry = true }) {
                                                    Text("Use PIN instead")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(onResult: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed) return
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                        }
                        onResult(false)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onResult(true)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric login for Habte")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use account password")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Biometric authentication failed to start", t)
            onResult(false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinLockScreen(onUnlocked: () -> Unit, onUseBiometricInstead: (() -> Unit)?) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun tryUnlock() {
        if (SettingsRepository.verifyPin(pin)) {
            onUnlocked()
        } else {
            error = "Incorrect PIN."
            pin = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter your PIN",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4 && it.all(Char::isDigit)) {
                        pin = it
                        error = null
                    }
                },
                placeholder = { Text("****", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.width(160.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { tryUnlock() },
                enabled = pin.length == 4,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock")
            }
            if (onUseBiometricInstead != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onUseBiometricInstead) {
                    Text("Use biometrics instead")
                }
            }
        }
    }
}
