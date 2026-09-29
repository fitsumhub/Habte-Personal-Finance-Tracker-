# Contributing to Habte – Personal Finance ሀብቴ

Thank you for your interest in contributing to Habte! This document explains how to get started.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Adding Bank Support](#adding-bank-support)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)

---

## Code of Conduct

Be respectful and constructive. We're building tools for the Ethiopian community together.

---

## How to Contribute

### Reporting Bugs
Use the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.yml) template.

### Suggesting Features
Use the [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) template.

### Adding Bank SMS Parsers
This is the most impactful contribution! See [Adding Bank Support](#adding-bank-support).

### Code Contributions
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes
4. Run tests: `./gradlew testDebugUnitTest`
5. Submit a Pull Request

---

## Development Setup

### Requirements
- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- Android SDK API 23–36

### Clone & Build
```bash
git clone https://github.com/fitsumhub/Habte-Personal-Finance-Tracker-.git
cd Habte-Personal-Finance-Tracker-
./gradlew assembleDebug
```

### Running Tests
```bash
# Unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug
```

---

## Adding Bank Support

The most common contribution is adding an SMS parser for a new bank.

### Steps

1. **Collect the SMS format** — Get a real SMS from the bank (replace account number and balance with Xs):
   ```
   Your CBE account XXXXXX has been debited ETB XXX.XX on DD/MM/YYYY...
   ```

2. **Open an Issue** — Create a Feature Request issue with the SMS template. Our team will implement the parser with proper regex patterns and tests.

3. **(Advanced) Implement yourself** — Look at existing parsers in:
   ```
   app/src/main/java/com/mobile/data/SmsReceiver.kt
   ```
   Follow the existing pattern for your bank. Add unit tests covering:
   - Normal transaction
   - Boundary amounts (0.01, 999,999.99)
   - Missing fields (null safety)
   - Multi-language formats

---

## Pull Request Process

1. Ensure all tests pass: `./gradlew testDebugUnitTest`
2. Ensure lint passes: `./gradlew lintDebug`
3. Update `CHANGELOG.md` (or note it in the PR description)
4. Reference any related issues with `Fixes #123`
5. Request review from `@fitsumhub`

### PR Title Format
```
feat: Add Nib Bank SMS parser
fix: Handle null PDU on Samsung dual-SIM
docs: Update bank support table in README
```

---

## Code Style

- **Kotlin** — Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Coroutines** — Use `SupervisorJob` for background scopes; never use raw `CoroutineScope(Dispatchers.IO)`
- **Null Safety** — All `BroadcastReceiver.onReceive` contexts must be null-checked
- **Database** — Never use `lateinit var db`; use the thread-safe `getDb(context)` pattern
- **Error Handling** — Wrap `BroadcastReceiver.goAsync()` finish calls in `runCatching`

---

*Thank you for making Habte better for everyone!* 🇪🇹
