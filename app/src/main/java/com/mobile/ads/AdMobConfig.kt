package com.mobile.ads

/**
 * Central configuration for every AdMob ad unit used in the app.
 *
 * PRODUCTION CONFIGURATION — publisher ca-app-pub-2695951066960188
 * App ID: ca-app-pub-2695951066960188~8919692609  (set in AndroidManifest.xml)
 *
 * Ad unit IDs below are the real production units for this app.
 * USE_TEST_ADS is set to false for production/release builds.
 *
 * Ad placement rules:
 *   - Banner:       home, analytics, settings screens only
 *   - Interstitial: after export actions and analytics deep-dives
 *   - Rewarded:     user-initiated "remove ads for 1 hour" in Settings / Support
 *   - Native:       transaction history feed & tools screens
 *   - App Open:     cold start & background-to-foreground return (authenticated state only)
 *
 * IMPORTANT: Never show ads on PIN entry, add/edit transaction, transfer,
 * payment, or any financial-action screen.
 */
object AdMobConfig {

    // ── Production Ad Unit IDs (ca-app-pub-2695951066960188) ─────────────────────────
    // These are the real ad unit IDs for com.fitsumhub.habtetracker.
    // Format: ca-app-pub-<publisher-id>/<ad-unit-id>
    private const val BANNER_PRODUCTION_AD_UNIT_ID       = "ca-app-pub-2695951066960188/4991766196"
    private const val INTERSTITIAL_PRODUCTION_AD_UNIT_ID = "ca-app-pub-2695951066960188/5279019397"
    private const val REWARDED_PRODUCTION_AD_UNIT_ID     = "ca-app-pub-2695951066960188/8249782023"
    private const val NATIVE_PRODUCTION_AD_UNIT_ID       = "ca-app-pub-2695951066960188/4504070330"
    private const val APP_OPEN_PRODUCTION_AD_UNIT_ID     = "ca-app-pub-2695951066960188/9657248105"

    // ── Google's official test ad unit IDs (kept for reference / local dev) ──────────
    // These earn no revenue and should NEVER be used in a Play Store release.
    // To use during local development, flip USE_TEST_ADS back to true.
    private const val TEST_BANNER_AD_UNIT_ID             = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID       = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_AD_UNIT_ID           = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE_ADVANCED_AD_UNIT_ID    = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN_AD_UNIT_ID           = "ca-app-pub-3940256099942544/9257390722"

    // ── Environment switch ────────────────────────────────────────────────────────────
    // false  → production IDs (Play Store / real revenue)
    // true   → Google test IDs (local debug builds, no revenue, no policy risk)
    private const val USE_TEST_ADS = false

    // ── Public accessors — always use these, never hardcode IDs in screens ────────────
    val bannerAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else BANNER_PRODUCTION_AD_UNIT_ID

    val interstitialAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else INTERSTITIAL_PRODUCTION_AD_UNIT_ID

    val rewardedAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_REWARDED_AD_UNIT_ID else REWARDED_PRODUCTION_AD_UNIT_ID

    val nativeAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_NATIVE_ADVANCED_AD_UNIT_ID else NATIVE_PRODUCTION_AD_UNIT_ID

    val appOpenAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_APP_OPEN_AD_UNIT_ID else APP_OPEN_PRODUCTION_AD_UNIT_ID

    /**
     * Screens allowed to show a banner ad. Everything not listed here —
     * including every money-movement or authentication screen —
     * must never call BannerAdView.
     */
    val BANNER_ALLOWED_ROUTES: Set<String> = setOf("home", "analytics", "settings")

    /** True if [route] is one of the screens allowed to render a banner ad. */
    fun isBannerAllowedOn(route: String): Boolean = route in BANNER_ALLOWED_ROUTES
}

