package com.mobile.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobConfigTest {

    @Test
    fun testNativeAdUnitIdMatchesRequiredAdMobUnit() {
        val expected = "ca-app-pub-2695951066960188/6891483968"
        assertEquals(expected, AdMobConfig.nativeAdUnitId)
    }

    @Test
    fun testAllProductionAdUnitIdsFollowAdMobFormat() {
        val pattern = Regex("""^ca-app-pub-\d{16}/\d{10}$""")
        assertTrue("Banner ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.bannerAdUnitId.matches(pattern))
        assertTrue("Interstitial ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.interstitialAdUnitId.matches(pattern))
        assertTrue("Rewarded ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.rewardedAdUnitId.matches(pattern))
        assertTrue("Native ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.nativeAdUnitId.matches(pattern))
    }

    @Test
    fun testSensitiveRoutesAreNotAllowedForBannerAds() {
        assertFalse(AdMobConfig.isBannerAllowedOn("pin"))
        assertFalse(AdMobConfig.isBannerAllowedOn("add_transaction"))
        assertFalse(AdMobConfig.isBannerAllowedOn("transfer"))
        assertFalse(AdMobConfig.isBannerAllowedOn("payment"))
        assertFalse(AdMobConfig.isBannerAllowedOn("security"))
    }
}

