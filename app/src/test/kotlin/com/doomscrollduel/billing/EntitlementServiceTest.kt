package com.doomscrollduel.billing

import com.doomscrollduel.domain.billing.Entitlement
import com.doomscrollduel.domain.billing.EntitlementCodec
import com.doomscrollduel.domain.billing.EntitlementStatus
import com.doomscrollduel.domain.billing.ProPlan
import com.doomscrollduel.domain.billing.ProView
import com.doomscrollduel.domain.billing.PurchaseOutcome
import com.doomscrollduel.domain.billing.PurchaseOutcomes
import com.doomscrollduel.domain.billing.ServerVerdict
import com.doomscrollduel.domain.billing.toMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementServiceTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val t0 = 1_800_000_000_000L

    private class FakeCache(var stored: Entitlement = Entitlement.None) : EntitlementCache {
        var saves = 0
        override fun load() = stored
        override fun save(entitlement: Entitlement) { stored = entitlement; saves++ }
    }

    private class FakeSource : EntitlementSource {
        val updates = MutableSharedFlow<Entitlement?>(extraBufferCapacity = 8)
        override fun observe() = updates
    }

    private fun ent(status: EntitlementStatus, until: Long, renew: Boolean = false, verified: Long = t0) =
        Entitlement(status, ProPlan.MONTHLY, until, renew, verified)

    @Test fun `cached pro works with no network and no server answer`() = runTest {
        val cache = FakeCache(ent(EntitlementStatus.ACTIVE, t0 + 5 * day, renew = true))
        val service = EntitlementService(FakeSource(), cache, { t0 + testScheduler.currentTime }, backgroundScope)
        runCurrent()
        assertTrue(service.isPro)
    }

    @Test fun `a server update changes the answer and is cached`() = runTest {
        val cache = FakeCache()
        val source = FakeSource()
        val service = EntitlementService(source, cache, { t0 + testScheduler.currentTime }, backgroundScope)
        runCurrent()
        assertFalse(service.isPro)
        source.updates.emit(ent(EntitlementStatus.ACTIVE, t0 + 30 * day, renew = true))
        runCurrent()
        assertTrue(service.isPro)
        assertEquals(EntitlementStatus.ACTIVE, cache.stored.status)
    }

    @Test fun `server says no entitlement, pro is gone and the cache is cleared`() = runTest {
        val cache = FakeCache(ent(EntitlementStatus.ACTIVE, t0 + 5 * day, renew = true))
        val source = FakeSource()
        val service = EntitlementService(source, cache, { t0 + testScheduler.currentTime }, backgroundScope)
        runCurrent()
        source.updates.emit(null)
        runCurrent()
        assertFalse(service.isPro)
        assertEquals(Entitlement.None, cache.stored)
    }

    @Test fun `pro ends by itself at the moment the paid time runs out`() = runTest {
        val cache = FakeCache(ent(EntitlementStatus.CANCELED, t0 + hour))
        val service = EntitlementService(FakeSource(), cache, { t0 + testScheduler.currentTime }, backgroundScope)
        runCurrent()
        assertTrue(service.view.value is ProView.Ending)
        advanceTimeBy(hour - 10)
        runCurrent()
        assertTrue(service.isPro)
        advanceTimeBy(20)
        runCurrent()
        assertEquals(ProView.Expired, service.view.value)
    }

    @Test fun `grace period then on hold`() = runTest {
        val cache = FakeCache(ent(EntitlementStatus.GRACE, t0 + 2 * day, renew = true))
        val service = EntitlementService(FakeSource(), cache, { t0 + testScheduler.currentTime }, backgroundScope)
        runCurrent()
        assertTrue(service.view.value is ProView.GracePeriod)
        advanceTimeBy(2 * day + 10)
        runCurrent()
        assertEquals(ProView.OnHold, service.view.value)
    }

    @Test fun `a restore that finds a live subscription brings pro back`() = runTest {
        val source = FakeSource()
        val service = EntitlementService(source, FakeCache(), { t0 + testScheduler.currentTime }, backgroundScope)
        runCurrent()
        source.updates.emit(ent(EntitlementStatus.ACTIVE, t0 + 10 * day, renew = true))
        runCurrent()
        assertTrue(service.view.value is ProView.Active)
    }
}

class PurchaseOutcomeTest {
    private val now = 1_000L

    @Test fun `server verdicts map to outcomes`() {
        assertTrue(PurchaseOutcomes.fromVerdict(ServerVerdict(EntitlementStatus.ACTIVE, now + 1, now)) is PurchaseOutcome.Verified)
        assertTrue(PurchaseOutcomes.fromVerdict(ServerVerdict(EntitlementStatus.GRACE, now + 1, now)) is PurchaseOutcome.Verified)
        assertEquals(PurchaseOutcome.Pending, PurchaseOutcomes.fromVerdict(ServerVerdict(EntitlementStatus.PENDING, 0, now)))
        assertEquals(PurchaseOutcome.Rejected, PurchaseOutcomes.fromVerdict(ServerVerdict(EntitlementStatus.EXPIRED, now - 1, now)))
        assertEquals(PurchaseOutcome.Rejected, PurchaseOutcomes.fromVerdict(ServerVerdict(EntitlementStatus.ACTIVE, now - 1, now)))
        assertEquals(PurchaseOutcome.Rejected, PurchaseOutcomes.fromVerdict(ServerVerdict(EntitlementStatus.ON_HOLD, now + 5, now)))
    }

    @Test fun `every outcome except cancel has a message`() {
        assertEquals(null, PurchaseOutcome.Cancelled.toMessage())
        listOf(
            PurchaseOutcome.Pending, PurchaseOutcome.NothingToRestore, PurchaseOutcome.NotSignedIn, PurchaseOutcome.NoNetwork,
            PurchaseOutcome.StoreUnavailable, PurchaseOutcome.Rejected, PurchaseOutcome.Failed("x"),
            PurchaseOutcome.Verified(EntitlementStatus.ACTIVE, 1),
        ).forEach { assertTrue(it.toString(), it.toMessage() != null) }
    }

    @Test fun `restore picks the best of several tokens`() {
        val ok = PurchaseOutcome.Verified(EntitlementStatus.ACTIVE, 5)
        assertEquals(ok, PurchaseOutcomes.best(listOf(PurchaseOutcome.Rejected, ok, PurchaseOutcome.NoNetwork)))
        assertEquals(PurchaseOutcome.Pending, PurchaseOutcomes.best(listOf(PurchaseOutcome.Rejected, PurchaseOutcome.Pending)))
        assertEquals(PurchaseOutcome.NoNetwork, PurchaseOutcomes.best(listOf(PurchaseOutcome.Rejected, PurchaseOutcome.NoNetwork)))
        assertEquals(PurchaseOutcome.NothingToRestore, PurchaseOutcomes.best(emptyList()))
    }
}

class EntitlementCodecTest {
    @Test fun `round trip`() {
        val e = Entitlement(EntitlementStatus.GRACE, ProPlan.YEARLY, 123L, true, 99L)
        assertEquals(e, EntitlementCodec.decode(EntitlementCodec.encode(e)))
    }

    @Test fun `damaged or empty text grants nothing`() {
        assertEquals(Entitlement.None, EntitlementCodec.decode(null))
        assertEquals(Entitlement.None, EntitlementCodec.decode("{not json"))
        assertEquals(Entitlement.None, EntitlementCodec.decode(""))
    }

    @Test fun `unknown status or plan from the server grants nothing`() {
        val e = EntitlementCodec.fromFields(mapOf("status" to "SUPER_PRO", "plan" to "LIFETIME", "accessUntilMs" to 9_999_999_999_999L))
        assertEquals(EntitlementStatus.NONE, e.status)
        assertEquals(null, e.plan)
    }

    @Test fun `firestore numbers of any type are read`() {
        val e = EntitlementCodec.fromFields(mapOf("status" to "ACTIVE", "plan" to "MONTHLY", "accessUntilMs" to 5, "verifiedAtMs" to 6L, "autoRenewing" to true))
        assertEquals(Entitlement(EntitlementStatus.ACTIVE, ProPlan.MONTHLY, 5L, true, 6L), e)
    }
}
