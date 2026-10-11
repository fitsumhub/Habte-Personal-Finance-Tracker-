package com.mobile.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobConfigTest {

    @Test
    fun testProductionAdUnitIdsMatchRequiredAdMobUnits() {
        assertEquals("ca-app-pub-2695951066960188/2877758352", AdMobConfig.BANNER_PRODUCTION_AD_UNIT_ID)
        assertEquals("ca-app-pub-2695951066960188/4504070330", AdMobConfig.NATIVE_PRODUCTION_AD_UNIT_ID)
        assertEquals("ca-app-pub-2695951066960188/6891483968", AdMobConfig.NATIVE_SECONDARY_PRODUCTION_AD_UNIT_ID)
        assertEquals("ca-app-pub-2695951066960188/5279019397", AdMobConfig.INTERSTITIAL_PRODUCTION_AD_UNIT_ID)
        assertEquals("ca-app-pub-2695951066960188/1456865476", AdMobConfig.INTERSTITIAL_ALT_PRODUCTION_AD_UNIT_ID)
        assertEquals("ca-app-pub-2695951066960188/9657248105", AdMobConfig.APP_OPEN_PRODUCTION_AD_UNIT_ID)
        assertEquals("ca-app-pub-2695951066960188/3322365506", AdMobConfig.APP_OPEN_ALT_PRODUCTION_AD_UNIT_ID)
    }

    @Test
    fun testAllProductionAdUnitIdsFollowAdMobFormat() {
        val pattern = Regex("""^ca-app-pub-\d{16}/\d{10}$""")
        assertTrue("Banner ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.BANNER_PRODUCTION_AD_UNIT_ID.matches(pattern))
        assertTrue("Interstitial ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.INTERSTITIAL_PRODUCTION_AD_UNIT_ID.matches(pattern))
        assertTrue("Native primary ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.NATIVE_PRODUCTION_AD_UNIT_ID.matches(pattern))
        assertTrue("Native secondary ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.NATIVE_SECONDARY_PRODUCTION_AD_UNIT_ID.matches(pattern))
        assertTrue("App Open ad unit ID must follow ca-app-pub-XXX/YYY", AdMobConfig.APP_OPEN_PRODUCTION_AD_UNIT_ID.matches(pattern))
    }

    @Test
    fun testTestAdsSwitchSwitchesBetweenTestAndProduction() {
        AdMobConfig.useTestAds = true
        assertEquals(AdMobConfig.TEST_BANNER_AD_UNIT_ID, AdMobConfig.bannerAdUnitId)
        assertEquals(AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID, AdMobConfig.interstitialAdUnitId)
        assertEquals(AdMobConfig.TEST_NATIVE_ADVANCED_AD_UNIT_ID, AdMobConfig.nativeAdUnitId)
        assertEquals(AdMobConfig.TEST_APP_OPEN_AD_UNIT_ID, AdMobConfig.appOpenAdUnitId)

        AdMobConfig.useTestAds = false
        assertEquals(AdMobConfig.BANNER_PRODUCTION_AD_UNIT_ID, AdMobConfig.bannerAdUnitId)
        assertEquals(AdMobConfig.INTERSTITIAL_PRODUCTION_AD_UNIT_ID, AdMobConfig.interstitialAdUnitId)
        assertEquals(AdMobConfig.NATIVE_PRODUCTION_AD_UNIT_ID, AdMobConfig.nativeAdUnitId)
        assertEquals(AdMobConfig.APP_OPEN_PRODUCTION_AD_UNIT_ID, AdMobConfig.appOpenAdUnitId)
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
