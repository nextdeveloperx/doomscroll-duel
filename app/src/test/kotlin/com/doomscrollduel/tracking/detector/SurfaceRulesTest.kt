package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceRulesTest {
    @Test
    fun `view id is compared without the package prefix`() {
        val rules = SurfaceRules(listOf(SurfaceRule(TrackedApp.YOUTUBE, setOf("reel_recycler"))))
        assertTrue(rules.matchesScroll(TrackedApp.YOUTUBE, null, "com.google.android.youtube:id/reel_recycler"))
        assertFalse(rules.matchesScroll(TrackedApp.YOUTUBE, null, "com.google.android.youtube:id/reel_recycler_extra"))
        assertFalse(rules.matchesScroll(TrackedApp.YOUTUBE, null, null))
        assertFalse(rules.matchesScroll(TrackedApp.INSTAGRAM, null, "x:id/reel_recycler"))
    }

    @Test
    fun `parses the json format and skips unknown packages`() {
        val rules = SurfaceRulesParser.parse(
            """{"version":1,"rules":[
                {"package":"com.instagram.android","viewIds":["a"],"verified":true},
                {"package":"com.example.unknown","viewIds":["b"]},
                {"package":"com.snapchat.android","extra":"ignored"}]}""",
        )
        assertTrue(rules.isConfigured(TrackedApp.INSTAGRAM))
        assertFalse(rules.isConfigured(TrackedApp.SNAPCHAT))
        assertEquals(2, rules.rules().size)
    }

    @Test(expected = Exception::class)
    fun `malformed json throws`() {
        SurfaceRulesParser.parse("{ not json")
    }

    @Test
    fun `the bundled rules file parses and covers all four apps`() {
        val text = File("src/main/assets/surface_rules.json").readText()
        val rules = SurfaceRulesParser.parse(text)
        assertEquals(TrackedApp.entries.toSet(), rules.rules().map { it.app }.toSet())
        assertTrue(rules.isConfigured(TrackedApp.INSTAGRAM))
        assertTrue(rules.isConfigured(TrackedApp.YOUTUBE))
    }
}
