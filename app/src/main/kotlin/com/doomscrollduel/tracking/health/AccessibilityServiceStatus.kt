package com.doomscrollduel.tracking.health

/** Reads the system's list of enabled accessibility services (a colon-separated string). */
object AccessibilityServiceStatus {
    /**
     * [enabledServices] is `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, e.g.
     * `com.a/.Svc:com.doomscrollduel/com.doomscrollduel.tracking.service.ReelAccessibilityService`.
     * The class part may be written short (".Svc") or fully qualified.
     */
    fun isEnabled(enabledServices: String?, packageName: String, serviceClassName: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        val wantedClass = qualify(packageName, serviceClassName)
        return enabledServices.split(':').any { entry ->
            val pkg = entry.substringBefore('/', missingDelimiterValue = "").trim()
            val cls = entry.substringAfter('/', missingDelimiterValue = "").trim()
            pkg.equals(packageName, ignoreCase = true) &&
                cls.isNotEmpty() &&
                qualify(pkg, cls).equals(wantedClass, ignoreCase = true)
        }
    }

    private fun qualify(packageName: String, className: String): String =
        if (className.startsWith('.')) packageName + className else className
}
