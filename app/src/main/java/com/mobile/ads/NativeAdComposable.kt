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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.mobile.data.SettingsRepository

/**
 * Reusable Native Advanced Ad Composable fully compliant with Google Mobile Ads policies.
 *
 * AdMob Policy & Implementation Highlights:
 * 1. Attribution: Displays a prominent, un-obscured "Ad" badge within the ad view.
 * 2. Visual Separation: Clear card styling with dedicated boundaries and contrast to avoid
 *    confusion with organic transaction items or accidental clicks.
 * 3. Asset Binding: Explicitly binds headline, body, icon, call-to-action, media, and advertiser
 *    to NativeAdView so impression and click tracking registers correctly.
 * 4. Responsive Media: Gracefully hides MediaView (View.GONE) when no video/image media exists,
 *    keeping the ad compact, and scales MediaView when media is present.
 * 5. Lifecycle Management: Automatically destroys the native ad when leaving composition to prevent
 *    native memory leaks.
 * 6. Ad-Free State: Respects user-earned ad-free periods (SettingsRepository.isAdFreeActive()),
 *    rendering nothing (zero height) during ad-free intervals or on load failure.
 */
@Composable
fun NativeAdComposable(
    modifier: Modifier = Modifier
) {
    // Respect ad-free time earned through rewarded actions
    val adFreeUntil by SettingsRepository.adFreeUntilMillis.collectAsState()
    if (System.currentTimeMillis() < adFreeUntil) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    if (loadFailed) return

    // Load ad on entering composition; clean up native memory on dispose
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
            nativeAd?.destroy()
        }
    }

    val ad = nativeAd ?: return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx -> buildNativeAdView(ctx) },
        update = { adView -> bindNativeAd(adView, ad) }
    )
}

/**
 * Builds the NativeAdView hierarchy styled to harmonize with Habte's dark luxury theme
 * while strictly adhering to Google AdMob native ad display guidelines.
 */
private fun buildNativeAdView(context: Context): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    // ── Outer Card Background ────────────────────────────────────────────────────────
    val cardBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(0xFF181E29.toInt()) // Surface elevated dark navy/charcoal
        setStroke(dp(1), 0xFF2C3748.toInt()) // Subtle card border
    }

    // ── Mandatory "Ad" Attribution Badge (Policy Requirement) ─────────────────────────
    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 10f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF0F172A.toInt()) // Dark text on bright badge
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
        setTextColor(0xFF94A3B8.toInt()) // Muted slate text
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(6)
        }
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    val attributionRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(adBadge)
        addView(advertiser)
    }

    // ── Headline ──────────────────────────────────────────────────────────────────────
    val headline = TextView(context).apply {
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFFF8FAFC.toInt()) // High contrast off-white
        maxLines = 2
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
        setTextColor(0xFFCBD5E1.toInt()) // Subtle secondary text
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
            bottomMargin = dp(6)
        }
    }

    // ── App / Sponsor Icon ────────────────────────────────────────────────────────────
    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(12)
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
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
    val media = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(150)
        ).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
    }

    // ── Call To Action Button ─────────────────────────────────────────────────────────
    val callToAction = Button(context).apply {
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        val ctaBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(0xFF00C853.toInt()) // Habte signature emerald green
        }
        background = ctaBg
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(38)
        ).apply {
            topMargin = dp(6)
        }
    }

    // ── Root Container ────────────────────────────────────────────────────────────────
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
    }
}

/**
 * Binds native ad content to registered views and finalizes registration with the SDK.
 */
private fun bindNativeAd(adView: NativeAdView, nativeAd: NativeAd) {
    // Headline (mandatory)
    (adView.headlineView as? TextView)?.text = nativeAd.headline

    // Body (optional)
    (adView.bodyView as? TextView)?.let {
        it.text = nativeAd.body
        it.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Advertiser (optional)
    (adView.advertiserView as? TextView)?.let {
        it.text = nativeAd.advertiser
        it.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Icon (optional)
    (adView.iconView as? ImageView)?.let { iconView ->
        val icon = nativeAd.icon
        if (icon != null) {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }
    }

    // Media (optional — collapse to 0 height when no media content is present)
    adView.mediaView?.let { mediaView ->
        val mediaContent = nativeAd.mediaContent
        if (mediaContent != null && (mediaContent.hasVideoContent() || mediaContent.aspectRatio > 0f || mediaContent.mainImage != null)) {
            mediaView.mediaContent = mediaContent
            mediaView.visibility = View.VISIBLE
        } else {
            mediaView.visibility = View.GONE
        }
    }

    // Call to Action (optional)
    (adView.callToActionView as? Button)?.let {
        it.text = nativeAd.callToAction
        it.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // Register the populated ad object with the NativeAdView for impression & click measurement
    adView.setNativeAd(nativeAd)
}

