package com.doomscrollduel.tracking.health

enum class TrackingIssue {
    NONE,

    /** The person has not agreed to the Accessibility disclosure, so nothing is counted. Shown before anything else. */
    CONSENT_NEEDED,

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
    /** The person agreed to the current Accessibility disclosure. Without it the service ignores every event. */
    val consentGiven: Boolean = true,
) {
    val issue: TrackingIssue
        get() = when {
            !consentGiven -> TrackingIssue.CONSENT_NEEDED
            !accessibilityEnabled -> TrackingIssue.ACCESSIBILITY_OFF
            !batteryUnrestricted -> TrackingIssue.BATTERY_RESTRICTED
            else -> TrackingIssue.NONE
        }
}
