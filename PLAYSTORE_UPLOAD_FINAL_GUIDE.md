# 🚀 HABTE (ሀብቴ) — Final Play Store Deployment Guide

This is your complete, definitive, copy-paste ready guide to publish **Habte - Personal Finance** to the Google Play Store.

---

## 📌 App & Console Credentials Cheat Sheet

| Parameter | Value |
| :--- | :--- |
| **App Name** | `Habte - Personal Finance` (ሀብቴ) |
| **Package Name** | `com.fitsumhub.habtetracker` |
| **Version** | `1.0.2` (Version Code `3`) |
| **Console App ID** | `4975579816095438878` |
| **Developer ID** | `9140832679312928999` |
| **Direct Console Link** | [Open Habte in Play Console](https://play.google.com/console/u/0/developers/9140832679312928999/app/4975579816095438878/) |
| **Key Management Link** | [Open Key Management](https://play.google.com/console/u/0/developers/9140832679312928999/app/4975579816095438878/keymanagement) |
| **Production Track Link** | [Open Production Track](https://play.google.com/console/u/0/developers/9140832679312928999/app/4975579816095438878/tracks/production) |
| **Production Bundle File** | `C:\Habte-Financial-Tracker\app\build\outputs\bundle\release\app-release.aab` |
| **Upload Certificate File** | `C:\Habte-Financial-Tracker\upload_certificate.pem` |
| **Keystore File** | `C:\Habte-Financial-Tracker\keystore\habte-release.jks` *(alias: `habte`)* |
| **Upload Key SHA-256** | `49:86:6E:F3:42:71:E9:14:71:4B:3D:D9:64:47:C9:07:DD:39:02:29:55:12:1A:81:9B:CC:20:B8:10:DA:FF:E6` |
| **Google Signing Key SHA-256** | `D5:7D:11:91:12:B0:02:E0:AD:97:2D:48:3F:F9:32:3E:B7:B6:A8:4C:2C:59:AA:13:36:36:06:9A:CB:1B:F9:B4` |

---

## STEP 1: Sync Upload Key (Key Management)

Your current local release build is signed with your permanent key (`49:86:6E...`), whereas your Play Console currently has an older upload key (`F7:EE:B6...`) registered from initial setup.

1. Go to 👉 [Play Console Key Management](https://play.google.com/console/u/0/developers/9140832679312928999/app/4975579816095438878/keymanagement).
2. Under **Upload key certificate**, click the link: **Request upload key reset**.
3. In the popup dialog:
   - **Reason for request**: Select **"I lost my upload key"** or **"Using a new keystore"**.
   - **Upload certificate**: Upload the certificate file:
     ```
     C:\Habte-Financial-Tracker\upload_certificate.pem
     ```
4. Click **Submit** / **Request**.
5. Google will send an email confirmation. As soon as the reset is effective (or if you already completed this), proceed to the steps below.

---

## STEP 2: Main Store Listing (Copy & Paste)

Go to: **Grow > Store presence > Main store listing** in Play Console.

### 2.1 App Details
* **App name**:
  ```
  Habte - Personal Finance
  ```
* **Short description** *(77 / 80 characters)*:
  ```
  Track Ethiopian bank & wallet spending automatically from your SMS. Private.
  ```

* **Full description** *(Copy exactly)*:
  ```
  Habte automatically tracks your money across every major Ethiopian bank and mobile wallet — no manual entry, no spreadsheets, no bank logins required.

  HOW IT WORKS
  Habte reads the transaction SMS your bank and wallet apps send you, parses the amount, balance, and merchant, and turns it into a clean, organized transaction history — entirely on your device. Supports Commercial Bank of Ethiopia (CBE), Bank of Abyssinia, Awash Bank, Dashen Bank, Hibret Bank, telebirr, and many more Ethiopian banks and wallets.

  WHY HABTE

  📊 Automatic Transaction Tracking
  No manual entry. Every SMS-notified transaction is captured, categorized, and added to your history automatically.

  💰 Real Budgets & Analytics
  See income vs. expenses by week, month, or year. Break spending down by category or by bank. Track your net worth across every account in one place.

  🔔 Payment Reminders
  Never miss a bill. Set up recurring reminders for rent, subscriptions, and payments with custom lead times.

  🔒 Private by Design
  Your financial data never leaves your device. There is no cloud account, no login, and no server — everything is stored locally with PIN and biometric lock protecting the app itself.

  🏆 Achievement Certificates
  Celebrate real progress with Weekly, Monthly, and Yearly certificates generated from your actual savings — downloadable and shareable.

  📁 Export & Backup
  Export your transaction history as PDF, CSV, or JSON anytime, and restore from a backup whenever you need to.

  🧮 Built-in Financial Tools
  Loan (EMI) calculator, income tax estimator, currency converter, and a duplicate-transaction checker to keep your records clean.

  🇪🇹 Built for Ethiopia
  Optional Ethiopian calendar support alongside Gregorian, and full support for Ethiopian Birr (ETB) throughout.

  YOUR DATA STAYS YOURS
  Habte has no ads that access your financial data, no data-selling, and no account to hack. SMS parsing happens entirely on your device — nothing is ever uploaded to any server.
  ```

### 2.2 Graphic Assets
* **App Icon**: `512 x 512 px` PNG (from `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`).
* **Feature Graphic**: `1024 x 500 px` PNG/JPEG.
* **Phone Screenshots**: Minimum 2 screenshots (16:9 or 9:16 aspect ratio).
* **Tablet Screenshots**: 7-inch and 10-inch screenshots (minimum 1 each).

---

## STEP 3: Policy & App Content Declarations

Go to: **Policy and programs > App content** in Play Console.

### 3.1 Privacy Policy
* Enter your hosted Privacy Policy HTTPS URL (from `docs/privacy-policy.html`).
* *URL*: `https://fitsumhub.github.io/Habte-Personal-Finance-Tracker-/privacy-policy.html`

### 3.2 Sensitive Permissions — SMS Declaration
Google Play requires a declaration for `READ_SMS` and `RECEIVE_SMS`.

* **Core Functionality Category**: Select **Financial Management / Personal Finance**.
* **Why does your app need SMS permissions?** (Paste this exact justification):
  ```text
  Habte is an automatic personal finance tracker designed specifically for Ethiopian financial institutions (Commercial Bank of Ethiopia, Bank of Abyssinia, Telebirr, Awash Bank, Dashen Bank). Ethiopian banks communicate completed deposits, withdrawals, and balance updates exclusively via SMS text messages. 

  The app's core functionality relies entirely on reading and parsing incoming bank SMS messages locally on the user's device to automatically construct transaction logs, category budgets, and multi-bank account balances without requiring manual data entry or online bank login credentials. 

  No SMS content is ever transmitted off the user's device — all parsing and storage is executed 100% locally in an on-device SQLite database.
  ```
* **Video Proof Link**: Provide a YouTube/Google Drive link of a short video (15-30s) showing an incoming bank SMS automatically generating a transaction in the app.

### 3.3 Data Safety Form
* **Does your app collect or share user data?**: Select **Yes** *(due to Google AdMob SDK advertising identifier collection)*.
* **Is data encrypted in transit?**: **Yes**.
* **Do you provide a way for users to request deletion?**: **Yes** *(via Settings > Clear All Data)*.
* **Data Types**:
  * **Financial Info (Account Info / Transactions)**: **No** *(Data stays 100% on device, never collected or sent to any server)*.
  * **Device or Other IDs (Advertising ID)**: 
    - Collected? **Yes** *(Google Mobile Ads SDK)*.
    - Ephemeral? **No**.
    - Required or optional? **Required**.
    - Purposes: **Advertising or marketing**, **Analytics**.

### 3.4 Target Audience, Ads & Category
* **Target Audience**: `18 and over`.
* **Ads**: Select **Yes, my app contains ads**.
* **Financial Features**: Select **Personal Financial Management**.
* **Category**: **Finance**.
* **Content Rating (IARC)**: Complete questionnaire — Category: *Utility/Productivity*. Result will be **Everyone (3+) / PEGI 3**.

---

## STEP 4: Upload AAB & Release to Production

Go to: 👉 [Production Release](https://play.google.com/console/u/0/developers/9140832679312928999/app/4975579816095438878/tracks/production)

1. Click the blue button: **Create new release** (top right).
2. In the **App bundles** box, click **Upload** and choose:
   ```
   C:\Habte-Financial-Tracker\app\build\outputs\bundle\release\app-release.aab
   ```
3. Fill in release details:
   * **Release name**: `1.0.2 (3)`
   * **Release notes (`en-US`)**:
     ```text
     - Fixed background stability and battery optimizations for Samsung Galaxy and Android 15/16 devices.
     - Resolved unexpected deep sleep warnings by improving background database access.
     - Enhanced dual-SIM SMS detection for Ethiopian banking notifications.
     - Improved widget refresh performance on home and lock screens.
     - General performance enhancements and memory optimizations.
     ```
4. Click **Next** at the bottom right.
5. Review any items flagged by Google Play.
6. Click **Save** ➔ **Review release** ➔ **Start rollout to Production**!

---

## STEP 5: How to Build Future Updates

When you make updates in the future:

1. Open `app/build.gradle`.
2. Increment `versionCode` (e.g. `3`) and update `versionName` (e.g. `"1.0.2"`).
3. Open PowerShell in `c:\Habte-Financial-Tracker` and run:
   ```powershell
   .\gradlew.bat clean bundleRelease
   ```
4. The newly signed bundle will be ready at `app\build\outputs\bundle\release\app-release.aab` for your next release!

