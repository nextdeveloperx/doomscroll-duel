package com.doomscrollduel.tracking.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {
    private val pkg = "com.doomscrollduel"
    private val cls = "com.doomscrollduel.tracking.service.ReelAccessibilityService"

    @Test
    fun `finds the service in the enabled list in full or short form`() {
        assertTrue(AccessibilityServiceStatus.isEnabled("$pkg/$cls", pkg, cls))
        assertTrue(AccessibilityServiceStatus.isEnabled("a.b/.X:$pkg/.tracking.service.ReelAccessibilityService", pkg, cls))
        assertTrue(AccessibilityServiceStatus.isEnabled("a.b/.X:${pkg}/${cls.uppercase()}", pkg, cls))
    }

    @Test
    fun `not enabled when missing, empty or a similar name`() {
        assertFalse(AccessibilityServiceStatus.isEnabled(null, pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("", pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("a.b/.X:c.d/.Y", pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("$pkg.other/$cls", pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("garbage", pkg, cls))
    }

    @Test
    fun `accessibility off beats battery restricted`() {
        assertEquals(TrackingIssue.ACCESSIBILITY_OFF, TrackingHealth(false, false).issue)
        assertEquals(TrackingIssue.ACCESSIBILITY_OFF, TrackingHealth(false, true).issue)
        assertEquals(TrackingIssue.BATTERY_RESTRICTED, TrackingHealth(true, false).issue)
        assertEquals(TrackingIssue.NONE, TrackingHealth(true, true).issue)
    }

    @Test
    fun `phone makers map to a family`() {
        assertEquals(OemFamily.XIAOMI, OemFamily.from("Xiaomi", "Redmi"))
        assertEquals(OemFamily.XIAOMI, OemFamily.from("Xiaomi", "POCO"))
        assertEquals(OemFamily.REALME_OPPO, OemFamily.from("realme", "realme"))
        assertEquals(OemFamily.REALME_OPPO, OemFamily.from("OPPO", "OPPO"))
        assertEquals(OemFamily.REALME_OPPO, OemFamily.from("OnePlus", "OnePlus"))
        assertEquals(OemFamily.VIVO, OemFamily.from("vivo", "vivo"))
        assertEquals(OemFamily.VIVO, OemFamily.from("vivo", "iQOO"))
        assertEquals(OemFamily.SAMSUNG, OemFamily.from("samsung", "samsung"))
        assertEquals(OemFamily.OTHER, OemFamily.from("Google", "google"))
        assertEquals(OemFamily.OTHER, OemFamily.from(null, null))
    }

    @Test
    fun `every family starts with the two generic steps`() {
        OemFamily.entries.forEach { oem ->
            assertEquals(
                listOf(BatteryStep.OPEN_BATTERY_LIST, BatteryStep.PICK_APP_UNRESTRICTED),
                BatteryGuide.stepsFor(oem).take(2),
            )
        }
    }

    @Test
    fun `each maker gets its own extra steps and nothing from another maker`() {
        assertEquals(2, BatteryGuide.stepsFor(OemFamily.OTHER).size)
        assertTrue(BatteryStep.XIAOMI_AUTOSTART in BatteryGuide.stepsFor(OemFamily.XIAOMI))
        assertTrue(BatteryStep.REALME_AUTO_LAUNCH in BatteryGuide.stepsFor(OemFamily.REALME_OPPO))
        assertTrue(BatteryStep.VIVO_BACKGROUND_POWER in BatteryGuide.stepsFor(OemFamily.VIVO))
        assertTrue(BatteryStep.SAMSUNG_SLEEPING_APPS in BatteryGuide.stepsFor(OemFamily.SAMSUNG))
        assertFalse(BatteryStep.XIAOMI_AUTOSTART in BatteryGuide.stepsFor(OemFamily.SAMSUNG))
        assertFalse(BatteryStep.LOCK_IN_RECENTS in BatteryGuide.stepsFor(OemFamily.SAMSUNG))
    }
}
