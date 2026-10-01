package com.doomscrollduel.tracking.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import com.doomscrollduel.tracking.detector.SurfaceRules
import com.doomscrollduel.tracking.model.TrackedApp

/**
 * Answers one question: "is this app's reel screen open right now?" It looks for the Shorts/Reels pager by its
 * layout id (the same ids used to count reels) in the window that is in front.
 *
 * PRIVACY: this is the ONLY file allowed to look at the window tree, and it reads nothing from it. It asks the
 * system "is there a view with this id?" and throws the answer's nodes away. It never reads text, descriptions,
 * names or children. `scripts/check-service-privacy.sh` enforces that nothing else touches `rootInActiveWindow`
 * or `findAccessibilityNodeInfosByViewId`, and that this file touches nothing else.
 */
class ReelScreenProbe(private val rules: () -> SurfaceRules) {

    fun isOpen(service: AccessibilityService, app: TrackedApp): Boolean {
        val ids = rules().viewIdsOf(app)
        if (ids.isEmpty()) return false
        val root = service.rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != app.packageName) return false
            for (id in ids) {
                val matches = root.findAccessibilityNodeInfosByViewId("${app.packageName}:id/$id")
                val found = !matches.isNullOrEmpty()
                matches?.forEach(::release)
                if (found) return true
            }
            return false
        } finally {
            release(root)
        }
    }

    @Suppress("DEPRECATION")
    private fun release(node: android.view.accessibility.AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) node.recycle()
    }
}
