package com.mobile.ads

/**
 * Central configuration for every AdMob ad unit used in the app.
 *
 * PRODUCTION CONFIGURATION — publisher ca-app-pub-2695951066960188
 * App ID: ca-app-pub-2695951066960188~8919692609  (set in AndroidManifest.xml)
 *
 * Initial ad unit — Banner:
 *   ca-app-pub-2695951066960188/2877758352
 *
 * Available additional ad units:
 *   - Native Advanced: ca-app-pub-2695951066960188/4504070330
 *   - Native Advanced: ca-app-pub-2695951066960188/6891483968
 *   - Interstitial:    ca-app-pub-2695951066960188/5279019397
 *   - Interstitial:    ca-app-pub-2695951066960188/1456865476
 *   - App Open:        ca-app-pub-2695951066960188/9657248105
 *   - App Open:        ca-app-pub-2695951066960188/3322365506
 *
 * Ad placement policy:
 *   - Starts with one conservative adaptive banner placement on the Home Dashboard.
 *   - Native ads appear only in non-critical content lists (Tools, Transaction Feed).
 *   - Interstitial ads appear only after deliberate export actions.
 *   - App Open ads trigger only upon valid background-to-foreground resumes with cooldown.
 *   - Never show ads on PIN entry, biometric auth, add/edit transaction, transfer,
 *     payment, or any financial-action screen.
 */
object AdMobConfig {

    // ── Production Ad Unit IDs (ca-app-pub-2695951066960188) ─────────────────────────
    const val BANNER_PRODUCTION_AD_UNIT_ID           = "ca-app-pub-2695951066960188/2877758352"
    const val NATIVE_PRODUCTION_AD_UNIT_ID           = "ca-app-pub-2695951066960188/4504070330"
    const val NATIVE_SECONDARY_PRODUCTION_AD_UNIT_ID = "ca-app-pub-2695951066960188/6891483968"
    const val INTERSTITIAL_PRODUCTION_AD_UNIT_ID     = "ca-app-pub-2695951066960188/5279019397"
    const val INTERSTITIAL_ALT_PRODUCTION_AD_UNIT_ID = "ca-app-pub-2695951066960188/1456865476"
    const val APP_OPEN_PRODUCTION_AD_UNIT_ID         = "ca-app-pub-2695951066960188/9657248105"
    const val APP_OPEN_ALT_PRODUCTION_AD_UNIT_ID     = "ca-app-pub-2695951066960188/3322365506"
    const val REWARDED_PRODUCTION_AD_UNIT_ID         = "ca-app-pub-2695951066960188/5279019397"

    // ── Google's official test ad unit IDs (for development/testing) ─────────────────
    // Protects developers against Invalid Traffic (IVT) policy violations.
    const val TEST_BANNER_AD_UNIT_ID             = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID       = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_AD_UNIT_ID           = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_NATIVE_ADVANCED_AD_UNIT_ID    = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_APP_OPEN_AD_UNIT_ID           = "ca-app-pub-3940256099942544/9257390722"

    // ── Environment switch ────────────────────────────────────────────────────────────
    // Automatically uses official test ads in debug builds (development/QA) to protect
    // against Invalid Traffic bans, and production IDs in release builds.
    var useTestAds: Boolean = try {
        com.mobile.BuildConfig.DEBUG
    } catch (_: Throwable) {
        false
    }

    // ── Ad format activation flags (Requirement 3) ────────────────────────────────────
    // Start with one responsive adaptive banner on dashboard.
    // Interstitial and App Open ads are kept disabled initially during publisher review.
    var enableBannerAds: Boolean       = true
    var enableNativeAds: Boolean       = true
    var enableInterstitialAds: Boolean = false
    var enableAppOpenAds: Boolean      = false

    // ── Dynamic public accessors ──────────────────────────────────────────────────────
    val bannerAdUnitId: String
        get() = if (useTestAds) TEST_BANNER_AD_UNIT_ID else BANNER_PRODUCTION_AD_UNIT_ID

    val interstitialAdUnitId: String
        get() = if (useTestAds) TEST_INTERSTITIAL_AD_UNIT_ID else INTERSTITIAL_PRODUCTION_AD_UNIT_ID

    val rewardedAdUnitId: String
        get() = if (useTestAds) TEST_REWARDED_AD_UNIT_ID else REWARDED_PRODUCTION_AD_UNIT_ID

    val nativeAdUnitId: String
        get() = if (useTestAds) TEST_NATIVE_ADVANCED_AD_UNIT_ID else NATIVE_PRODUCTION_AD_UNIT_ID

    val nativeSecondaryAdUnitId: String
        get() = if (useTestAds) TEST_NATIVE_ADVANCED_AD_UNIT_ID else NATIVE_SECONDARY_PRODUCTION_AD_UNIT_ID

    val appOpenAdUnitId: String
        get() = if (useTestAds) TEST_APP_OPEN_AD_UNIT_ID else APP_OPEN_PRODUCTION_AD_UNIT_ID

    /**
     * Screens allowed to show a banner ad. Starts with the Home Dashboard.
     * All sensitive money-movement or authentication screens are strictly excluded.
     */
    val BANNER_ALLOWED_ROUTES: Set<String> = setOf("home")

    /** True if [route] is one of the screens allowed to render a banner ad. */
    fun isBannerAllowedOn(route: String): Boolean = route in BANNER_ALLOWED_ROUTES
}
