# Changelog

All notable changes to Habte – Personal Finance are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.2] – 2024-09-13

### Fixed
- **Samsung crash (critical):** `lateinit property db has not been initialized` when app process was cold-started by an alarm, SMS, or widget. Eliminated all `lateinit var db` in repositories; replaced with thread-safe `@Volatile` + `@Synchronized getDb(context)` double-checked locking.
- **SQLiteConstraintException on MIGRATION_5_6:** Added deduplication step before creating the unique composite index.
- **Widget `DeadObjectException`:** Each `appWidgetManager.updateAppWidget()` call is now wrapped in an individual `try-catch(Throwable)` to handle Samsung launcher restarts gracefully.
- **Samsung dual-SIM SMS PDU format:** `SmsReceiver` now handles both `Array<*>` and `ArrayList<*>` PDU bundle types.
- **BroadcastReceiver crash on goAsync finish:** All receivers now wrap `pendingResult.finish()` in `runCatching`.
- **Coroutine scope cascade failure:** All background repositories now use `SupervisorJob() + CoroutineExceptionHandler` scopes — a single coroutine failure no longer cancels the whole repository scope.

### Changed
- **AdMob App ID:** Updated `AndroidManifest.xml` to production App ID.
- **`HabteApplication`:** Each repository `init()` call is now in an independent `try-catch` to prevent cascade failure.

### Tested On
- Samsung Galaxy A54 (One UI 6.1, Android 14)
- TECNO CM6 (Android 16 / API 36)
- Pixel 6 emulator (Android 15 / API 35)

---

## [1.0.1] – 2024-09-05

### Changed
- Performance improvements in transaction list rendering
- Minor UI refinements on the Home screen

### Fixed
- Occasional empty state flicker on first launch

---

## [1.0.0] – 2024-08-25

### Added
- Initial Play Store release
- Multi-bank SMS parsing (Telebirr, CBE, BOA, Awash, Dashen, Abay, Wegagen, Zemen)
- Home screen widgets: Net Worth, Weekly Spending, Budget Pulse, Quick Tracker, Multi-Bank
- Biometric lock (Fingerprint / Face ID)
- Privacy Mode with `FLAG_SECURE`
- Balance masking
- Budget tracking with per-category limits
- Payment reminders with AlarmManager
- CSV export
- English + Amharic localization
- Material 3 dark/light/system theming
