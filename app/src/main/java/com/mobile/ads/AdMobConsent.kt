package com.mobile.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google's User Messaging Platform (UMP) — gathers GDPR/CCPA-style ad consent before any
 * ad is requested. This isn't optional polish: AdMob policy expects every publisher to run
 * this before serving ads, and skipping it risks ad serving being restricted at review, not
 * just a compliance gap for EEA/UK users specifically. Must be given the chance to resolve
 * before MobileAds.initialize() runs (see AdMobService.initialize).
 */
object AdMobConsent {
    private const val TAG = "AdMobConsent"

    /**
     * Checks if ads can be requested based on current consent status.
     */
    fun canRequestAds(context: Context): Boolean {
        return try {
            UserMessagingPlatform.getConsentInformation(context).canRequestAds()
        } catch (t: Throwable) {
            Log.e(TAG, "Error checking canRequestAds", t)
            true
        }
    }

    /**
     * Checks if privacy options (consent management) is required for the user's region (GDPR/EEA/UK).
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(context)
            consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (t: Throwable) {
            Log.e(TAG, "Error checking privacy options requirement", t)
            false
        }
    }

    /**
     * Requests/updates consent info and shows Google's consent form if one is required for
     * this user — outside regions that require it, [ConsentInformation] resolves this
     * near-instantly with nothing to show. [onComplete] always fires exactly once, whether
     * or not a form was shown or the request failed (e.g. offline), so ad initialization is
     * never blocked on it — consistent with every other ad path in this app degrading to
     * "no ad" rather than stalling anything.
     */
    fun gatherConsent(activity: Activity, onComplete: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }
        try {
            val params = ConsentRequestParameters.Builder().build()
            val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    if (activity.isFinishing || activity.isDestroyed) {
                        onComplete()
                        return@requestConsentInfoUpdate
                    }
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "Consent form error (${formError.errorCode}): ${formError.message}")
                            }
                            onComplete()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error showing consent form", t)
                        onComplete()
                    }
                },
                { requestError ->
                    Log.w(TAG, "Consent info update failed (${requestError.errorCode}): ${requestError.message}")
                    onComplete()
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error in gatherConsent", t)
            onComplete()
        }
    }

    /**
     * Presents the Google UMP Privacy Options form so users can review or change their
     * consent choices at any time (e.g., from the Settings screen).
     */
    fun showPrivacyOptionsForm(activity: Activity, onComplete: (error: String?) -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete("Activity is finishing")
            return
        }
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError != null) {
                    Log.w(TAG, "Privacy options form error (${formError.errorCode}): ${formError.message}")
                    onComplete(formError.message)
                } else {
                    Log.d(TAG, "Privacy options updated successfully")
                    onComplete(null)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error showing privacy options form", t)
            onComplete(t.message)
        }
    }
}
