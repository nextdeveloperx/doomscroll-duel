package com.doomscrollduel.domain.billing

import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.ProGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun ent(
        status: EntitlementStatus,
        until: Long = now + 10 * day,
        renew: Boolean = true,
        plan: ProPlan? = ProPlan.MONTHLY,
        verified: Long = now - 1000,
    ) = Entitlement(status, plan, until, renew, verified)

    @Test fun `never subscribed is free`() = assertEquals(ProView.Free, Entitlements.view(Entitlement.None, now))

    @Test fun `active and renewing is pro`() {
        val v = Entitlements.view(ent(EntitlementStatus.ACTIVE), now)
        assertTrue(v is ProView.Active && v.isPro)
    }

    @Test fun `cancelled keeps pro until the paid time ends then expires`() {
        val e = ent(EntitlementStatus.CANCELED, until = now + day, renew = false)
        assertTrue(Entitlements.view(e, now) is ProView.Ending)
        assertTrue(Entitlements.view(e, now + day - 1).isPro)
        assertEquals(ProView.Expired, Entitlements.view(e, now + day))
    }

    @Test fun `active but renewal switched off is shown as ending`() {
        assertTrue(Entitlements.view(ent(EntitlementStatus.ACTIVE, renew = false), now) is ProView.Ending)
    }

    @Test fun `grace period keeps pro, then goes on hold`() {
        val e = ent(EntitlementStatus.GRACE, until = now + 2 * day)
        assertTrue(Entitlements.view(e, now) is ProView.GracePeriod)
        assertEquals(ProView.OnHold, Entitlements.view(e, now + 2 * day))
    }

    @Test fun `on hold, paused, pending and expired are not pro`() {
        listOf(EntitlementStatus.ON_HOLD, EntitlementStatus.PAUSED, EntitlementStatus.PENDING, EntitlementStatus.EXPIRED)
            .forEach { assertFalse(it.name, Entitlements.view(ent(it), now).isPro) }
    }

    @Test fun `a renewing plan survives a renewal the phone has not heard about, but only for the slack`() {
        val e = ent(EntitlementStatus.ACTIVE, until = now - day)
        assertTrue(Entitlements.view(e, now).isPro)
        assertEquals(ProView.Expired, Entitlements.view(e, now - day + Entitlements.RENEWAL_SLACK_MS))
    }

    @Test fun `no slack for a plan that is not renewing`() {
        val e = ent(EntitlementStatus.ACTIVE, until = now - 1, renew = false)
        assertEquals(ProView.Expired, Entitlements.view(e, now))
    }

    @Test fun `turning the clock back more than a day fails closed`() {
        val e = ent(EntitlementStatus.ACTIVE, verified = now)
        assertEquals(ProView.ClockSuspect, Entitlements.view(e, now - 2 * day))
        assertTrue(Entitlements.view(e, now - day / 2).isPro) // a small drift is tolerated
    }

    @Test fun `clock check does not hide a plain free user`() {
        assertEquals(ProView.Free, Entitlements.view(Entitlement.None, 0))
    }

    @Test fun `next change is the end of access`() {
        assertEquals(day, Entitlements.nextChangeInMs(ent(EntitlementStatus.CANCELED, until = now + day, renew = false), now))
        assertEquals(day + Entitlements.RENEWAL_SLACK_MS, Entitlements.nextChangeInMs(ent(EntitlementStatus.ACTIVE, until = now + day), now))
        assertNull(Entitlements.nextChangeInMs(Entitlement.None, now))
        assertNull(Entitlements.nextChangeInMs(ent(EntitlementStatus.CANCELED, until = now - 1, renew = false), now))
    }
}

class FreeLimitsTest {
    @Test fun `free player gets exactly three duels a day`() {
        assertEquals(Access.Allowed, FreeLimits.canStartDuel(2, isPro = false))
        assertEquals(Access.NeedsPro(ProFeature.UNLIMITED_DUELS), FreeLimits.canStartDuel(3, isPro = false))
        assertEquals(Access.Allowed, FreeLimits.canStartDuel(50, isPro = true))
    }

    @Test fun `duels left`() {
        assertEquals(1, FreeLimits.duelsLeft(2, false))
        assertEquals(0, FreeLimits.duelsLeft(9, false))
        assertNull(FreeLimits.duelsLeft(9, true))
    }

    @Test fun `mode gating agrees with ProGate`() {
        for (mode in ChallengeMode.entries) {
            assertEquals(mode.name, mode.isPro, FreeLimits.featureFor(mode) != null)
            assertEquals(ProGate.canCreate(mode, false), FreeLimits.featureFor(mode)?.let { FreeLimits.check(it, false) } in listOf(null, Access.Allowed))
        }
    }
}

class PlanPricingTest {
    private fun offer(plan: ProPlan, micros: Long, cur: String = "INR") = PlanOffer(plan, "x", micros, cur, "t")

    @Test fun `yearly saving is computed from Play prices`() {
        // 99 a month, 799 a year: 1188 vs 799 -> 32%
        assertEquals(32, PlanPricing.yearlySavingPercent(listOf(offer(ProPlan.MONTHLY, 99_000_000), offer(ProPlan.YEARLY, 799_000_000))))
    }

    @Test fun `no saving claim when it cannot be honest`() {
        assertNull(PlanPricing.yearlySavingPercent(listOf(offer(ProPlan.MONTHLY, 99_000_000))))
        assertNull(PlanPricing.yearlySavingPercent(listOf(offer(ProPlan.MONTHLY, 99_000_000), offer(ProPlan.YEARLY, 1_200_000_000))))
        assertNull(PlanPricing.yearlySavingPercent(listOf(offer(ProPlan.MONTHLY, 99_000_000), offer(ProPlan.YEARLY, 7_000_000, "USD"))))
    }

    @Test fun `yearly per month is written in the plan currency`() {
        val offers = listOf(offer(ProPlan.YEARLY, 120_000_000, "USD"))
        assertEquals("$10.00", PlanPricing.yearlyPerMonthText(offers, java.util.Locale.US))
        assertNull(PlanPricing.yearlyPerMonthText(listOf(offer(ProPlan.MONTHLY, 1)), java.util.Locale.US))
        assertNull(PlanPricing.yearlyPerMonthText(listOf(offer(ProPlan.YEARLY, 1, "???")), java.util.Locale.US))
    }

    @Test fun `base plan ids map to plans`() {
        assertEquals(ProPlan.MONTHLY, ProProduct.planOf("monthly"))
        assertEquals(ProPlan.YEARLY, ProProduct.planOf("yearly"))
        assertNull(ProProduct.planOf("weekly"))
    }
}
