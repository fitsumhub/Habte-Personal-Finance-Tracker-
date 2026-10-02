# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Google Mobile Ads (AdMob) references android.media.LoudnessCodecController, a platform
# class newer than this project's compileSdk (34). It's only reached by an internal
# audio-loudness code path Play Services Ads uses on newer OS versions at runtime — the
# real class exists there; it's only missing from this build's android.jar, which is a
# build-time-only gap R8 can't verify is safe on its own. Regenerate this block from
# app/build/outputs/mapping/release/missing_rules.txt if a future AdMob version adds more.
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
-dontwarn android.media.LoudnessCodecController

# Keep Room-generated code and entities intact (Room's own consumer rules cover most of
# this already, but the explicit entity keep is cheap insurance against reflection-based
# schema validation issues after minification).
-keep class com.mobile.data.db.** { *; }
-keep class androidx.room.** { *; }

# Keep Habte application, domain data models, and services
-keep class com.mobile.HabteApplication { *; }
-keep class com.mobile.data.** { *; }
-keep class com.mobile.ads.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin enums looked up by name (Room stores enum columns as their `.name` string —
# see PaymentReminderEntity.repeat / ReminderRepeat.valueOf()).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Google Mobile Ads (AdMob) and User Messaging Platform (UMP)
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-keep public class com.google.android.ump.** { public *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

