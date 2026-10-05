package com.mobile.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobConfigTest {

    @Test
    fun testProductionAdUnitIdsMatchRequiredAdMobUnits() {
        assertEquals("ca-app-pub-2695951066960188/4294306445", AdMobConfig.bannerAdUnitId)
        assertEquals("ca-app-pub-2695951066960188/4504070330", AdMobConfig.nativeAdUnitId)
        assertEquals("ca-app-pub-2695951066960188/5279019397", AdMobConfig.interstitialAdUnitId)
        assertEquals("ca-app-pub-2695951066960188/6891483968", AdMobConfig.nativeSecondaryAdUnitId)
        assertEquals("ca-app-pub-2695951066960188/9657248105", AdMobConfig.appOpenAdUnitId)
    }

    @Test
    fun testAllProductionAdUnitIdsFollowAdMobFormat() {
        val pattern = Regex("""^ca-app-pub-\d{16}/\d{10}$""")
        assertTrue("Banner ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.bannerAdUnitId.matches(pattern))
        assertTrue("Interstitial ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.interstitialAdUnitId.matches(pattern))
        assertTrue("Native primary ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.nativeAdUnitId.matches(pattern))
        assertTrue("Native secondary ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.nativeSecondaryAdUnitId.matches(pattern))
        assertTrue("App Open ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.appOpenAdUnitId.matches(pattern))
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

