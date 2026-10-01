package com.doomscrollduel.domain.analytics

import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.billing.ProPlan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingSink : AnalyticsSink {
    val logged = mutableListOf<Pair<String, Map<String, String>>>()
    val props = mutableListOf<Pair<String, String>>()
    override fun log(name: String, params: Map<String, String>) { logged += name to params }
    override fun setUserProperty(name: String, value: String) { props += name to value }
}

class AnalyticsTest {
    private fun everyEvent(): List<AnalyticsEvent> = buildList {
        add(AnalyticsEvent.OnboardingCompleted(0))
        add(AnalyticsEvent.OnboardingCompleted(5))
        AnalyticsPermission.entries.forEach { add(AnalyticsEvent.PermissionGranted(it)) }
        AnalyticsMode.entries.forEach { m ->
            add(AnalyticsEvent.DuelCreated(m, 24, 50, 100))
            add(AnalyticsEvent.DuelCreated(m, 7, 3, 0))
            add(AnalyticsEvent.DuelAccepted(m, 0))
            add(AnalyticsEvent.DuelAccepted(m, 100))
            DuelResultToken.entries.forEach { r ->
                add(AnalyticsEvent.DuelFinished(m, r, 0, false))
                add(AnalyticsEvent.DuelFinished(m, r, 999, true))
            }
        }
        add(AnalyticsEvent.LockTriggered(LockKind.TIMER, 1))
        add(AnalyticsEvent.LockTriggered(LockKind.TIMER, null))
        add(AnalyticsEvent.LockTriggered(LockKind.BEDTIME))
        add(AnalyticsEvent.LockTriggered(LockKind.FOCUS))
        UnlockResultToken.entries.forEach { add(AnalyticsEvent.UnlockRequested(it)) }
        add(AnalyticsEvent.PaywallViewed(null))
        ProFeature.entries.forEach { add(AnalyticsEvent.PaywallViewed(it)) }
        ProPlan.entries.forEach { add(AnalyticsEvent.SubscriptionStarted(it)) }
    }

    @Test fun `every event the app can build passes the privacy gate`() {
        for (e in everyEvent()) assertEquals(e.name + e.params, PolicyVerdict.Ok, AnalyticsPolicy.check(e))
    }

    @Test fun `every one of the nine events is covered`() {
        assertEquals(AnalyticsSpec.EVENTS.keys, everyEvent().map { it.name }.toSet())
        assertEquals(9, AnalyticsSpec.EVENTS.size)
    }

    @Test fun `events only carry parameters the spec allows`() {
        for (e in everyEvent()) assertTrue(e.name, AnalyticsSpec.EVENTS.getValue(e.name).containsAll(e.params.keys))
    }

    @Test fun `personal data is refused by the gate`() {
        val bad = listOf(
            "mode" to "Rohan Sharma",
            "mode" to "rohan@gmail.com",
            "mode" to "+91 98765 43210",
            "mode" to "9876543210",
            "mode" to "X".repeat(41),
            "mode" to "",
            "mode" to "Duel",
            "mode" to "duel ",
            "mode" to "a8Kd9sLq2PzX0fVbN3mQ7tYh1wRc5uEj4oGi6", // looks like an account id
            "mode" to "1727772000000", // a timestamp
        )
        for ((key, value) in bad) {
            val verdict = AnalyticsPolicy.check("duel_accepted", mapOf(key to value))
            assertTrue("$value was allowed", verdict is PolicyVerdict.Rejected)
        }
    }

    @Test fun `unknown events and unknown parameters are refused`() {
        assertTrue(AnalyticsPolicy.check("screen_view", emptyMap()) is PolicyVerdict.Rejected)
        assertTrue(AnalyticsPolicy.check("duel_created", mapOf("opponent" to "aman")) is PolicyVerdict.Rejected)
        assertTrue(AnalyticsPolicy.check("paywall_viewed", mapOf("user_id" to "u1")) is PolicyVerdict.Rejected)
        assertTrue(AnalyticsPolicy.check("paywall_viewed", (1..9).associate { "k$it" to "v" }) is PolicyVerdict.Rejected)
    }

    @Test fun `numbers are bucketed so exact counts never leave the phone`() {
        assertEquals("0", Buckets.reels(0))
        assertEquals("1_20", Buckets.reels(20))
        assertEquals("21_50", Buckets.reels(21))
        assertEquals("201_plus", Buckets.reels(10_000))
        assertEquals("other", Buckets.entryCoins(37))
        assertEquals("other", Buckets.durationHours(5))
        assertEquals("lt_1h", Buckets.acceptDelay(0))
        assertEquals("gt_24h", Buckets.acceptDelay(48))
    }

    @Test fun `nothing is sent until the person allows it`() {
        val sink = RecordingSink()
        var choice = UsageDataChoice.UNDECIDED
        val analytics = Analytics(sink, { choice })
        analytics.track(AnalyticsEvent.PaywallViewed(null))
        analytics.setPlan(true)
        choice = UsageDataChoice.DECLINED
        analytics.track(AnalyticsEvent.PaywallViewed(null))
        assertTrue(sink.logged.isEmpty() && sink.props.isEmpty())
        choice = UsageDataChoice.ALLOWED
        analytics.track(AnalyticsEvent.PaywallViewed(ProFeature.SQUAD_BATTLE))
        analytics.setPlan(false)
        assertEquals(listOf("paywall_viewed" to mapOf("trigger" to "squad_battle")), sink.logged)
        assertEquals(listOf("plan" to "free"), sink.props)
    }

    @Test fun `a rejected event is dropped and reported, never sent`() {
        val sink = RecordingSink()
        val rejected = mutableListOf<String>()
        val analytics = Analytics(sink, { UsageDataChoice.ALLOWED }, rejected::add)
        val bad = object : AnalyticsEvent("duel_created", mapOf("mode" to "Rohan")) {}
        analytics.track(bad)
        assertTrue(sink.logged.isEmpty())
        assertEquals(1, rejected.size)
    }

    @Test fun `permission_granted fires once on the change and never on the first look`() {
        val off = mapOf(AnalyticsPermission.ACCESSIBILITY to false, AnalyticsPermission.NOTIFICATIONS to true, AnalyticsPermission.BATTERY to false)
        val on = off + (AnalyticsPermission.ACCESSIBILITY to true)
        assertEquals(emptyList<AnalyticsPermission>(), PermissionTransitions.granted(null, on))
        assertEquals(listOf(AnalyticsPermission.ACCESSIBILITY), PermissionTransitions.granted(off, on))
        assertEquals(emptyList<AnalyticsPermission>(), PermissionTransitions.granted(on, on))
        assertEquals(emptyList<AnalyticsPermission>(), PermissionTransitions.granted(on, off)) // turning off is not an event
    }
}

/** The written spec and the code must list the same events and parameters. */
class AnalyticsSpecDocTest {
    private fun spec(): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, "docs/analytics-events.md")
            if (f.exists()) return f.readText()
            dir = dir.parentFile
        }
        error("docs/analytics-events.md not found")
    }

    @Test fun `every event and parameter is written in the spec`() {
        val text = spec()
        for ((name, params) in AnalyticsSpec.EVENTS) {
            assertTrue("event $name missing from docs/analytics-events.md", text.contains("`$name`"))
            for (p in params) assertTrue("parameter $p of $name missing", text.contains("`$p`"))
        }
    }

    @Test fun `the spec lists no event the code does not know`() {
        val documented = Regex("^\\| `([a-z_]+)` \\|[^|]+\\|[^|]+\\|[^|]+\\|\\s*$", RegexOption.MULTILINE).findAll(spec()).map { it.groupValues[1] }.toSet()
        assertEquals(AnalyticsSpec.EVENTS.keys, documented)
    }
}
