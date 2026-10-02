package com.mobile.ads

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.mobile.data.SettingsRepository
import com.mobile.ui.theme.LocalEthiopianColors

/**
 * Display styling presets for Native Advanced Ads.
 */
enum class NativeAdStyle {
    /** Full rich card with MediaView (for tools screen, dashboard, or prominent placements). */
    CARD,
    /** Compact feed card optimized for dense transaction feeds and lists. */
    FEED
}

/**
 * Professional Native Advanced Ad Wrapper composable.
 *
 * AdMob Policy & Performance Highlights:
 * 1. Prominent "Ad" Badge: High-contrast, un-obscured amber gold badge strictly complying with
 *    Google AdMob native ad display policy.
 * 2. Visual Separation: Distinct card styling with custom surface elevation and borders to prevent
 *    accidental clicks or confusion with app content.
 * 3. Full Asset Registration: Binds headline, body, icon, callToAction, media, advertiser,
 *    starRating, price, and store to NativeAdView for compliant attribution and measurement.
 * 4. Zero Layout Shift (CLS): Smoothly animates into view via [AnimatedVisibility] when loaded,
 *    and collapses to zero height on failure or ad-free state.
 * 5. Lifecycle Safety: Explicitly calls `nativeAd.destroy()` upon exiting composition to release
 *    native C++ memory allocations.
 * 6. Dynamic Theming: Adapts background, text, and border colors from [LocalEthiopianColors].
 */
@Composable
fun NativeAdComposable(
    modifier: Modifier = Modifier,
    style: NativeAdStyle = NativeAdStyle.CARD,
    adUnitId: String = AdMobConfig.nativeAdUnitId
) {
    // Respect user-earned ad-free periods
    val adFreeUntil by SettingsRepository.adFreeUntilMillis.collectAsState()
    if (System.currentTimeMillis() < adFreeUntil) return

    val context = LocalContext.current
    if (!AdMobConsent.canRequestAds(context)) return

    val colors = LocalEthiopianColors.current

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    if (loadFailed) return

    val currentAd = rememberUpdatedState(nativeAd)

    // Asynchronously request ad; release resources on dispose
    DisposableEffect(Unit) {
        AdMobService.loadNativeAd(
            context = context,
            onLoaded = { ad ->
                nativeAd = ad
            },
            onFailed = {
                loadFailed = true
            }
        )
        onDispose {
            currentAd.value?.destroy()
        }
    }

    val ad = nativeAd

    AnimatedVisibility(
        visible = ad != null && !loadFailed,
        enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(animationSpec = tween(250))
    ) {
        if (ad != null) {
            val surfaceColor = colors.surfaceElevated.toArgb()
            val borderColor = colors.border.toArgb()
            val textPrimary = colors.textPrimary.toArgb()
            val textMuted = colors.textMuted.toArgb()
            val emeraldColor = colors.emeraldPrimary.toArgb()
            val goldColor = colors.goldAccent.toArgb()

            AndroidView(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                factory = { ctx ->
                    buildEnhancedNativeAdView(
                        context = ctx,
                        style = style,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                        emeraldColor = emeraldColor,
                        goldColor = goldColor
                    )
                },
                update = { adView ->
                    bindEnhancedNativeAd(adView, ad, style)
                }
            )
        }
    }
}

/**
 * Constructs the NativeAdView hierarchy styled to match Habte's dark luxury aesthetic.
 */
private fun buildEnhancedNativeAdView(
    context: Context,
    style: NativeAdStyle,
    surfaceColor: Int,
    borderColor: Int,
    textPrimary: Int,
    textMuted: Int,
    emeraldColor: Int,
    goldColor: Int
): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    // ── Outer Card Background ────────────────────────────────────────────────────────
    val cardBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(surfaceColor)
        setStroke(dp(1), borderColor)
    }

    // ── Mandatory "Ad" Attribution Badge (Policy Requirement) ─────────────────────────
    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 10f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF0F172A.toInt()) // Dark slate text on vibrant amber
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(0xFFF59E0B.toInt()) // Warm amber gold
        }
        background = badgeBg
        setPadding(dp(5), dp(2), dp(5), dp(2))
    }

    // ── Advertiser Name / Source ──────────────────────────────────────────────────────
    val advertiser = TextView(context).apply {
        textSize = 11f
        setTextColor(textMuted)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(6)
        }
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    // ── Star Rating / Price Chip ──────────────────────────────────────────────────────
    val starRating = TextView(context).apply {
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(goldColor)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(6)
        }
        visibility = View.GONE
    }

    val priceOrStore = TextView(context).apply {
        textSize = 11f
        setTextColor(textMuted)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(6)
        }
        visibility = View.GONE
    }

    val attributionRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(adBadge)
        addView(advertiser)
        addView(starRating)
        addView(priceOrStore)
    }

    // ── Headline ──────────────────────────────────────────────────────────────────────
    val headline = TextView(context).apply {
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(textPrimary)
        maxLines = if (style == NativeAdStyle.FEED) 1 else 2
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
        }
    }

    // ── Body ──────────────────────────────────────────────────────────────────────────
    val body = TextView(context).apply {
        textSize = 12f
        setTextColor(textMuted)
        maxLines = if (style == NativeAdStyle.FEED) 2 else 3
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        }
    }

    // ── App / Sponsor Icon ────────────────────────────────────────────────────────────
    val icon = ImageView(context).apply {
        val size = if (style == NativeAdStyle.FEED) dp(38) else dp(44)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = dp(12)
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
        clipToOutline = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
        }
    }

    val headerTextColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(attributionRow)
        addView(headline)
    }

    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(icon)
        addView(headerTextColumn)
    }

    // ── Media View (Image / Video) ────────────────────────────────────────────────────
    val mediaHeight = if (style == NativeAdStyle.FEED) dp(110) else dp(160)
    val media = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            mediaHeight
        ).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
        clipToOutline = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
        }
    }

    // ── Call To Action Button ─────────────────────────────────────────────────────────
    val callToAction = Button(context).apply {
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(emeraldColor)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(38)
        ).apply {
            topMargin = dp(6)
        }
    }

    // ── Content Container ─────────────────────────────────────────────────────────────
    val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBackground
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(headerRow)
        addView(body)
        addView(media)
        addView(callToAction)
    }

    return NativeAdView(context).apply {
        addView(contentContainer)
        headlineView = headline
        bodyView = body
        iconView = icon
        mediaView = media
        callToActionView = callToAction
        advertiserView = advertiser
        starRatingView = starRating
        priceView = priceOrStore
        storeView = priceOrStore
    }
}

/**
 * Binds native ad content to registered views with proper conditional visibility.
 */
private fun bindEnhancedNativeAd(
    adView: NativeAdView,
    nativeAd: NativeAd,
    style: NativeAdStyle
) {
    // Headline
    (adView.headlineView as? TextView)?.text = nativeAd.headline

    // Body
    (adView.bodyView as? TextView)?.let {
        it.text = nativeAd.body
        it.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Advertiser
    (adView.advertiserView as? TextView)?.let {
        it.text = nativeAd.advertiser
        it.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Star Rating
    (adView.starRatingView as? TextView)?.let { starView ->
        val rating = nativeAd.starRating
        if (rating != null && rating > 0.0) {
            starView.text = "★ ${String.format(java.util.Locale.US, "%.1f", rating)}"
            starView.visibility = View.VISIBLE
        } else {
            starView.visibility = View.GONE
        }
    }

    // Price or Store
    (adView.priceView as? TextView)?.let { priceView ->
        val price = nativeAd.price
        val store = nativeAd.store
        val label = when {
            !price.isNullOrBlank() && !store.isNullOrBlank() -> "$price • $store"
            !price.isNullOrBlank() -> price
            !store.isNullOrBlank() -> store
            else -> null
        }
        if (label != null) {
            priceView.text = label
            priceView.visibility = View.VISIBLE
        } else {
            priceView.visibility = View.GONE
        }
    }

    // Icon
    (adView.iconView as? ImageView)?.let { iconView ->
        val icon = nativeAd.icon
        if (icon != null) {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }
    }

    // Media
    adView.mediaView?.let { mediaView ->
        val mediaContent = nativeAd.mediaContent
        val hasMedia = mediaContent != null && (
            mediaContent.hasVideoContent() ||
            mediaContent.aspectRatio > 0f ||
            mediaContent.mainImage != null
        )
        if (hasMedia) {
            mediaView.mediaContent = mediaContent
            mediaView.visibility = View.VISIBLE
        } else {
            mediaView.visibility = View.GONE
        }
    }

    // Call to Action
    (adView.callToActionView as? Button)?.let {
        it.text = nativeAd.callToAction
        it.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Register with SDK for impression and click tracking
    adView.setNativeAd(nativeAd)
}
