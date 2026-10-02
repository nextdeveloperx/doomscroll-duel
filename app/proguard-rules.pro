# R8 / ProGuard rules for release builds. The libraries (Hilt, Room, Firebase, Billing, WorkManager, kotlinx.serialization,
# Compose) ship their own consumer rules, and Android keeps every class named in the manifest by name (including the
# accessibility service, which Android Settings finds by class name). Only what WE do by reflection or by name needs a rule.

# kotlinx.serialization: our @Serializable DTOs are looked up through their generated serializers.
-keepclassmembers class com.doomscrollduel.** {
    *** Companion;
}
-keepclasseswithmembers class com.doomscrollduel.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The accessibility service's class name is compared with Android's enabled-services string
# (AccessibilityServiceStatus), so it must not be renamed. The manifest rule already keeps it; this makes it explicit.
-keepnames class com.doomscrollduel.tracking.service.ReelAccessibilityService

# Keep file names and line numbers in crash reports (Crashlytics uploads the mapping file, so reports de-obfuscate).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebRTC (Broadcast) calls its Java classes from native code by name.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Do not warn about optional annotations the libraries mention.
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
