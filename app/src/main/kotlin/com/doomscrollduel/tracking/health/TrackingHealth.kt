package com.doomscrollduel.tracking.health

enum class TrackingIssue {
    NONE,

    /** Counting is off: the accessibility service is disabled. Highest priority. */
    ACCESSIBILITY_OFF,

    /** Counting works but the system may stop it in the background. */
    BATTERY_RESTRICTED,
}

data class TrackingHealth(
    val accessibilityEnabled: Boolean,
    val batteryUnrestricted: Boolean,
    /**
     * Notifications can be shown. Counting works without them, but friend-unlock requests would not reach the
     * phone and the quiet counter notification would be hidden. Shown on Settings, not as a Home banner.
     */
    val notificationsEnabled: Boolean = true,
) {
    val issue: TrackingIssue
        get() = when {
            !accessibilityEnabled -> TrackingIssue.ACCESSIBILITY_OFF
            !batteryUnrestricted -> TrackingIssue.BATTERY_RESTRICTED
            else -> TrackingIssue.NONE
        }
}
