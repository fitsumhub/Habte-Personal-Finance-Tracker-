<div align="center">

<img src="assets/ic_launcher_round.png" alt="Habte Logo" width="96" height="96" />

# Habte – Personal Finance ሀብቴ

**The smart finance tracker built for Ethiopia 🇪🇹**

[![Play Store](https://img.shields.io/badge/Google_Play-Available-brightgreen?logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.fitsumhub.habtetracker)
[![Android](https://img.shields.io/badge/Android-6.0%2B-green?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![API Level](https://img.shields.io/badge/Min_SDK-23-orange)](https://developer.android.com/about/versions)
[![Version](https://img.shields.io/badge/Version-1.0.2-blue)](https://github.com/fitsumhub/Habte-Personal-Finance-Tracker-/releases)

[📥 Download on Google Play](https://play.google.com/store/apps/details?id=com.fitsumhub.habtetracker) · [📖 Privacy Policy](https://fitsumhub.github.io/Habte-Personal-Finance-Tracker-/privacy-policy.html) · [🐛 Report a Bug](https://github.com/fitsumhub/Habte-Personal-Finance-Tracker-/issues/new?template=bug_report.md) · [💡 Request a Feature](https://github.com/fitsumhub/Habte-Personal-Finance-Tracker-/issues/new?template=feature_request.md)

</div>

---

## 📖 Overview

**Habte** (ሀብቴ – Amharic for *"my wealth"*) is a privacy-first, fully offline personal finance tracker built for the Ethiopian market. It automatically reads bank SMS messages from **Telebirr**, **CBE**, **BOA**, **Awash**, **Dashen**, and more — no cloud sync, no account login, all data stays on your device.

> 🔒 **100% local processing.** Your financial data never leaves your phone.

---

## ✨ Features

### 🏦 Automated Transaction Sync
| Feature | Details |
|---|---|
| **Multi-Bank SMS Parsing** | Telebirr (127), CBE, BOA, Awash, Dashen, Abay, Wegagen, Zemen + more |
| **Real-Time Detection** | Transactions captured instantly via SMS BroadcastReceiver |
| **Smart Categorization** | AI-style rules auto-tag Food, Transport, Utilities, Income, Transfer |
| **Duplicate Prevention** | Hash-based deduplication prevents double entries |

### 🛡️ Security & Privacy
| Feature | Details |
|---|---|
| **Biometric Lock** | Fingerprint / Face ID on app launch |
| **Privacy Mode** | `FLAG_SECURE` + content blur in recent apps switcher |
| **Balance Masking** | One-tap hide for all amounts |
| **No Internet Required** | Core features work 100% offline |

### 📊 Financial Insights
| Feature | Details |
|---|---|
| **Net Worth Dashboard** | Real-time balance across all accounts |
| **Weekly Spending Chart** | Sparkline visualizations of spending trends |
| **Budget Tracking** | Set limits per category, track remaining |
| **Payment Reminders** | Smart reminders for recurring bills |
| **CSV Export** | Export full transaction history for external analysis |

### 🎨 Home Screen Widgets
| Widget | Description |
|---|---|
| **Net Worth** | Live total balance on your home screen |
| **Weekly Spending** | 7-day spend bar chart widget |
| **Budget Pulse** | Animated budget health indicator |
| **Quick Tracker** | One-tap manual transaction entry |
| **Multi-Bank** | Per-bank balance at a glance |

### 🌐 Localization
- **English** and **Amharic (አማርኛ)** — full UI translation
- Ethiopian date formats and currency (ETB ብር)

---

## 🛠️ Technology Stack

```
Language          Kotlin 2.x
UI                Jetpack Compose + Material 3
Architecture      Clean Architecture · Repository Pattern · MVVM
Async             Kotlin Coroutines · StateFlow · SupervisorJob
Database          Room (SQLite) + migrations
Storage           SharedPreferences (encrypted settings)
Background        BroadcastReceivers · AlarmManager · WorkManager
Monetization      Google AdMob (banner + interstitial)
Biometrics        AndroidX Biometric API
Analytics         Firebase Crashlytics (opt-in)
Build             Gradle (Kotlin DSL) · R8 minification
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio **Hedgehog (2023.1)** or newer
- JDK 17+
- Android SDK API 23–36

### Clone & Build

```bash
git clone https://github.com/fitsumhub/Habte-Personal-Finance-Tracker-.git
cd Habte-Personal-Finance-Tracker-
```

Open in Android Studio, wait for Gradle sync, then:

```bash
# Debug build (no keystore needed)
./gradlew assembleDebug

# Release AAB (requires keystore.properties — see below)
./gradlew bundleRelease
```

### Keystore Setup (Release builds only)

Create `keystore.properties` at the **project root** (this file is gitignored):

```properties
storeFile=keystore/your-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

> ⚠️ **Never commit `keystore.properties` or `.jks` files.** They are gitignored by default.

---

## 📁 Project Structure

```
Habte-Personal-Finance-Tracker-/
├── app/
│   ├── src/main/
│   │   ├── java/com/mobile/
│   │   │   ├── HabteApplication.kt          # App entry point, repo initialization
│   │   │   ├── MainActivity.kt
│   │   │   ├── ads/                         # AdMob configuration
│   │   │   ├── data/
│   │   │   │   ├── db/                      # Room database + DAOs + migrations
│   │   │   │   ├── FinanceRepository.kt     # Core transaction repo (thread-safe)
│   │   │   │   ├── SettingsRepository.kt    # Preferences (thread-safe lazy)
│   │   │   │   ├── SmsReceiver.kt           # SMS BroadcastReceiver (dual-SIM safe)
│   │   │   │   ├── SmsObserver.kt           # ContentObserver for SMS inbox
│   │   │   │   ├── *WidgetProvider.kt       # 5 home screen widgets
│   │   │   │   └── *Receiver.kt             # Alarm / boot / reminder receivers
│   │   │   └── ui/
│   │   │       ├── screens/                 # Compose screen files
│   │   │       ├── components/              # Reusable Compose components
│   │   │       └── theme/                   # Material 3 theming
│   │   ├── res/                             # XML resources, drawables, strings
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── docs/                                    # GitHub Pages site
│   ├── privacy-policy.html
│   ├── app-ads.txt
│   └── assetlinks.json
├── .github/
│   ├── workflows/
│   │   └── android.yml                      # CI: build + test on every push
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.md
│       └── feature_request.md
└── README.md
```

---

## 🔧 Architecture Deep Dive

### Crash Safety (v1.0.2)
All background components (SMS receivers, alarm receivers, widgets) are hardened:

- **Thread-safe DB access** — `lateinit var db` eliminated; replaced with `@Volatile` + `@Synchronized getDb(context)` double-checked locking
- **SupervisorJob scopes** — one coroutine failure cannot cascade to crash the whole process
- **Per-widget try-catch(Throwable)** — Samsung `DeadObjectException` from launcher restarts handled gracefully
- **Samsung dual-SIM PDU** — `SmsReceiver` handles both `Array<*>` and `ArrayList<*>` PDU formats

### Repository Pattern
```
UI Layer (Compose) → ViewModel → Repository → Room DB / SharedPreferences
                                            ↑
                              BroadcastReceiver (SMS) ──┘
```

---

## 📱 Supported Banks (SMS Parsing)

| Bank | Sender ID | Transaction Types |
|---|---|---|
| Telebirr | 127 | Send, Receive, Withdraw, Pay, Top-up |
| CBE (Commercial Bank of Ethiopia) | CBE, CBEBIRR | Debit, Credit, Balance |
| BOA (Bank of Abyssinia) | BOA | Debit, Credit |
| Awash Bank | AWASH | Debit, Credit |
| Dashen Bank | DASHEN | Debit, Credit |
| Abay Bank | ABAY | Debit, Credit |
| Wegagen Bank | WEGAGEN | Debit, Credit |
| Zemen Bank | ZEMEN | Debit, Credit |
| Nib Bank | NIB | Debit, Credit |
| Cooperative Bank | COOP | Debit, Credit |

---

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) first.

### Adding a New Bank Parser
1. Find the exact SMS format your bank sends (mask sensitive data)
2. Open an issue with the SMS template
3. We'll add the parser in the next release

### Development Workflow
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lintDebug

# Check for dependency updates
./gradlew dependencyUpdates
```

---

## 📄 Changelog

### v1.0.2 (Current)
- 🔧 **Fixed**: Samsung Device Care crash — `lateinit db` uninitialized on cold process start
- 🔧 **Fixed**: `SQLiteConstraintException` on MIGRATION_5_6 unique index
- 🔧 **Fixed**: Widget `DeadObjectException` on launcher restart
- 🔧 **Fixed**: Samsung dual-SIM SMS PDU format handling
- 🔧 **Fixed**: All background receivers now use `SupervisorJob` scopes
- ✅ **Tested**: Android 15 (API 35) and Android 16 (API 36)

### v1.0.1
- Performance improvements
- UI refinements

### v1.0.0
- Initial Play Store release

---

## 📄 License

```
MIT License

Copyright (c) 2024 Fitsumhub

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<div align="center">

**Built with ❤️ for Ethiopia by [Fitsumhub](https://github.com/fitsumhub)**

[⬆ Back to top](#habte--personal-finance-ሀብቴ)

</div>
