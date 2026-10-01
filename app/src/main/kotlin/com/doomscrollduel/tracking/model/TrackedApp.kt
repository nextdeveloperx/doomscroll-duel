package com.doomscrollduel.tracking.model

/** The four apps whose short-video feeds are counted. The package name is the only identity we use. */
enum class TrackedApp(val packageName: String) {
    INSTAGRAM("com.instagram.android"),
    YOUTUBE("com.google.android.youtube"),
    FACEBOOK("com.facebook.katana"),
    SNAPCHAT("com.snapchat.android");

    companion object {
        private val byPackage = entries.associateBy { it.packageName }

        fun fromPackage(packageName: String?): TrackedApp? = packageName?.let(byPackage::get)
    }
}
