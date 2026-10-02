package com.mobile.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.mobile.data.SettingsRepository

/**
 * Single entry point for every AdMob interaction in the app. Screens never talk to the
 * Google Mobile Ads SDK directly (except BannerAdView, which is itself a thin wrapper
 * around this same config) — everything routes through here so ad-loading concerns
 * (retry, preloading, graceful failure) live in one place instead of being duplicated
 * per screen.
 *
 * Ads are always optional. Every function here is designed so a network failure, a
 * missing fill, or a slow load degrades to "no ad shown" — never a crash and never a
 * blocked user action.
 */
object AdMobService {
    private const val TAG = "AdMobService"

    private var initialized = false

    // Interstitial/rewarded ads are one-shot: once `show()` is called, that specific
    // instance is spent and a fresh one must be loaded before it can be shown again.
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    /**
     * Step 1: gather ad consent (required before any ad request — see AdMobConsent), then
     * start the Mobile Ads SDK. This must run exactly once, before any ad is requested —
     * call it from MainActivity.onCreate (app start), passing the Activity itself since the
     * consent form needs one to display over. Safe to call more than once; only the first
     * call takes effect.
     */
    fun initialize(activity: Activity) {
        if (initialized) return
        initialized = true

        AdMobConsent.gatherConsent(activity) {
            // Declares this app as general-audience, not directed at children — matches
            // what a personal finance app should declare in the Play Console "Ads" /
            // "Target audience" sections; keep these two in sync if that ever changes.
            val requestConfiguration = RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            // Step 2: MobileAds.initialize() does its own network/setup work off the calling
            // thread and reports back via this listener — we don't need to block app startup
            // waiting for it, ad *loads* below will simply wait until the SDK is ready.
            MobileAds.initialize(activity.applicationContext, OnInitializationCompleteListener { status: InitializationStatus ->
                Log.d(TAG, "Mobile Ads SDK initialized: ${status.adapterStatusMap.keys}")
            })

            // Step 3: pre-warm the one-shot ad formats now, so an interstitial/rewarded ad is
            // already sitting ready by the time a screen actually wants to show one, instead
            // of the user waiting on a fresh network load at that moment.
            loadInterstitial(activity)
            loadRewarded(activity)
        }
    }

    // ── Interstitial ──────────────────────────────────────────────────────────────────

    /** Asynchronously loads one interstitial ad and holds onto it until shown. */
    fun loadInterstitial(context: Context) {
        InterstitialAd.load(
            context.applicationContext,
            AdMobConfig.interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Loading failures are expected and routine (no fill, offline, etc.) —
                    // log for diagnostics and just leave no ad preloaded; callers already
                    // treat "nothing loaded" as a normal, silent no-op.
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Shows the preloaded interstitial if one is ready, then immediately starts loading
     * the next one for the following opportunity. If nothing is loaded yet, this quietly
     * does nothing and calls [onClosed] right away — callers must never gate a user flow
     * on an ad actually appearing.
     */
    fun showInterstitialIfLoaded(activity: Activity, onClosed: () -> Unit = {}) {
        // Respect an ad-free grant earned by watching a rewarded ad — an interstitial
        // is exactly the kind of ad ad-free is meant to suppress.
        if (com.mobile.data.SettingsRepository.isAdFreeActive()) {
            onClosed()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial(activity)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                interstitialAd = null
                loadInterstitial(activity)
                onClosed()
            }
        }
        ad.show(activity)
    }

    // ── Rewarded ──────────────────────────────────────────────────────────────────────

    /** Asynchronously loads one rewarded ad and holds onto it until shown. */
    fun loadRewarded(context: Context) {
        RewardedAd.load(
            context.applicationContext,
            AdMobConfig.rewardedAdUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                }
            }
        )
    }

    /** True once a rewarded ad has finished preloading and is ready to show. */
    val isRewardedAdReady: Boolean get() = rewardedAd != null

    /**
     * Shows the preloaded rewarded ad if one is ready. [onReward] fires only if the user
     * watches to completion — never call it from anywhere else, since a reward must
     * actually be earned. [onClosed] always fires afterward regardless of outcome (earned,
     * skipped, or no ad available at all), so callers can safely re-enable their UI either
     * way without a separate error path to handle.
     */
    fun showRewardedIfLoaded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad == null) {
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded(activity)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                loadRewarded(activity)
                onClosed()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { onReward() })
    }

    // ── Native ────────────────────────────────────────────────────────────────────────

    /**
     * Loads one native ad and hands it to [onLoaded]. The caller takes ownership of the
     * returned [NativeAd] and is responsible for calling `NativeAd.destroy()` once it's no
     * longer displayed — NativeAdComposable does this automatically for you.
     */
    fun loadNativeAd(context: Context, onLoaded: (NativeAd) -> Unit, onFailed: (LoadAdError) -> Unit = {}) {
        if (SettingsRepository.isAdFreeActive()) {
            Log.d(TAG, "Ad-free active; skipping native ad load")
            return
        }
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()
        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()

        val loader = AdLoader.Builder(context.applicationContext, AdMobConfig.nativeAdUnitId)
            .forNativeAd { nativeAd -> onLoaded(nativeAd) }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Native ad failed to load: ${error.message}")
                    onFailed(error)
                }
            })
            .withNativeAdOptions(adOptions)
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }
}
