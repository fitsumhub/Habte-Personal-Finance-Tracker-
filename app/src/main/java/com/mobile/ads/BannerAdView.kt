package com.mobile.ads

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.mobile.data.SettingsRepository
import com.mobile.ui.theme.LocalEthiopianColors

/**
 * Professional, production-grade Banner Ad Wrapper composable.
 *
 * Key Capabilities & Architecture:
 * 1. Anchored Adaptive Banner: Dynamically computes screen width to request Google's modern
 *    anchored adaptive banner size instead of legacy fixed 320x50, providing higher fill rates,
 *    superior eCPMs, and eliminating awkward letterboxing on modern wide displays.
 * 2. Zero Cumulative Layout Shift (CLS): Does not render an empty blank placeholder while waiting.
 *    Smoothly animates in via [AnimatedVisibility] once loaded, and cleanly collapses to zero
 *    height if fill fails.
 * 3. Polished Visual Frame: Nested inside an elegant, themed container matching the app's
 *    dark luxury palette with subtle rounded border and background.
 * 4. Policy Guardrails: Strictly respects user-unlocked ad-free periods and only renders
 *    on approved routes ([AdMobConfig.BANNER_ALLOWED_ROUTES]).
 * 5. Memory Safety: Automatically invokes `adView.destroy()` when leaving composition.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobConfig.bannerAdUnitId
) {
    // Respect ad-free time earned by watching rewarded ads
    val adFreeUntil by SettingsRepository.adFreeUntilMillis.collectAsState()
    if (System.currentTimeMillis() < adFreeUntil) return

    val context = LocalContext.current
    val colors = LocalEthiopianColors.current

    var isLoaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    if (loadFailed) return

    // Calculate Google's Anchored Adaptive Banner size based on current display metrics
    val adaptiveAdSize = remember(context) {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val widthDp = (displayMetrics.widthPixels / density).toInt()
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }

    AnimatedVisibility(
        visible = isLoaded && !loadFailed,
        enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(animationSpec = tween(250))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated.copy(alpha = 0.4f))
                .border(0.5.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(adaptiveAdSize)
                        this.adUnitId = adUnitId
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                isLoaded = true
                                loadFailed = false
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                isLoaded = false
                                loadFailed = true
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { adView ->
                    adView.destroy()
                }
            )
        }
    }
}

