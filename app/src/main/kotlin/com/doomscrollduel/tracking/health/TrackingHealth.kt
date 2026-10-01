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
) {
    val issue: TrackingIssue
        get() = when {
            !accessibilityEnabled -> TrackingIssue.ACCESSIBILITY_OFF
            !batteryUnrestricted -> TrackingIssue.BATTERY_RESTRICTED
            else -> TrackingIssue.NONE
        }
}
