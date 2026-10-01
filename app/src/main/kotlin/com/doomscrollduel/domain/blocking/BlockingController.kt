package com.doomscrollduel.domain.blocking

import com.doomscrollduel.core.common.DayKeys
import com.doomscrollduel.domain.challenge.lock.LockDecision
import com.doomscrollduel.domain.challenge.lock.StrictLock
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.Instant
import java.time.ZoneId

// ----- ports: everything the controller needs from the outside ---------------------------------

fun interface ClockSource {
    fun sample(): ClockSample
}

fun interface ZoneSource {
    fun zone(): ZoneId
}

/** Where settings live. [current] must be instant (kept in memory); [save] may write to disk in the background. */
interface BlockingSettingsSource {
    fun current(): BlockingSettings

    fun save(settings: BlockingSettings)
}

/** Settings that screens can also watch. */
interface ObservableBlockingSettings : BlockingSettingsSource {
    val flow: kotlinx.coroutines.flow.StateFlow<BlockingSettings>
}

/** Where the lock, pass and request history live across restarts. */
interface BlockingStateStore {
    fun load(): BlockingState

    fun save(state: BlockingState)
}

/** A snapshot for screens: the lock, the pass, the window, the friend quota. Never stored. */
data class BlockingStatus(
    val settings: BlockingSettings,
    val lock: StrictLockState,
    val pass: FriendPass?,
    val window: ActiveWindow?,
    val askQuotaLeft: Int,
    val askPending: Boolean,
    val nowMs: Long,
) {
    val lockRunning: Boolean get() = lock is StrictLockState.Locked
    val lockRemainingMs: Long get() = (lock as? StrictLockState.Locked)?.remainingMs ?: 0L
}

/**
 * The blocking engine's brain. The accessibility service reports what the user is doing (a reel was counted, a reel
 * screen opened or closed) and asks what to do; Settings asks to change things; a worker asks when to wake next.
 * Everything is decided from timestamps, never from timers that must fire on time, so Doze, a killed app and a
 * late worker can delay housekeeping but cannot let a reel through.
 *
 * Thread-safe: the service, the Settings screen and WorkManager all call it.
 */
class BlockingController(
    private val settingsSource: BlockingSettingsSource,
    private val store: BlockingStateStore,
    private val clock: ClockSource,
    private val zones: ZoneSource,
) {
    private var state: BlockingState = store.load()
    private var gate: GateSession? = null

    // ----- reading --------------------------------------------------------------------------------

    /** Brings every timer up to date and returns the picture. Saves only if something changed structurally. */
    @Synchronized
    fun status(): BlockingStatus {
        val now = clock.sample()
        refresh(now)
        return statusAt(now)
    }

    /** Should the service look at the screen at all right now? False saves battery when nothing could block. */
    @Synchronized
    fun isWatching(): Boolean {
        val now = clock.sample()
        refresh(now)
        return BlockingEngine.isWatching(settingsSource.current(), state.lock, state.pass, instant(now), zones.zone())
    }

    /** The duration to wait before the next housekeeping run, or null if nothing is scheduled. Never below 1 s or above 24 h. */
    @Synchronized
    fun nextWakeDelayMs(): Long? {
        val now = clock.sample()
        refresh(now)
        val settings = settingsSource.current()
        val zone = zones.zone()
        val candidates = mutableListOf<Long>()
        (state.lock as? StrictLockState.Locked)?.let { candidates += it.remainingMs }
        state.pass?.let { candidates += it.remainingMs }
        state.pendingUnlock?.let { candidates += (it.requestedAtMs + UnlockRules.REQUEST_TTL.toMillis() - now.calendarMs) }
        TimeWindows.nextBoundary(settings.bedtime, settings.focus, instant(now), zone)?.let { candidates += it.toEpochMilli() - now.calendarMs }
        candidates += DayKeys.millisUntilNextDay(now.calendarMs, zone)
        return candidates.filter { it > 0 }.minOrNull()?.coerceIn(MIN_WAKE_MS, MAX_WAKE_MS)
    }

    // ----- what the service reports ---------------------------------------------------------------

    /**
     * A reel was counted. [dayTotal] is today's total including it. Returns true when this reel just started the
     * timer-lock, so the service can send the user back right away.
     */
    @Synchronized
    fun onReelCounted(dayTotal: Int): Boolean {
        val now = clock.sample()
        refresh(now)
        val config = settingsSource.current().lockConfig() ?: return false
        val before = state.lock
        val after = StrictLock.onReelCounted(config, before, now, zones.zone(), dayTotal)
        if (after === before) return false
        state = state.copy(lock = after)
        val started = before !is StrictLockState.Locked && after is StrictLockState.Locked
        if (started) store.save(state)
        return started
    }

    /** A reel screen just opened. Starts a gate session if needed and returns what to do. */
    @Synchronized
    fun onReelScreenOpened(): BlockAction {
        val now = clock.sample()
        refresh(now)
        val current = gate
        gate = if (current == null || current.hasEnded(now.elapsedMs)) GateSession(now.elapsedMs) else current.seen(now.elapsedMs)
        return decide(now)
    }

    /** What to do right now, without touching the gate session. For the overlay's once-a-second refresh while no reel screen is open. */
    @Synchronized
    fun peek(): BlockAction {
        val now = clock.sample()
        refresh(now)
        return decide(now)
    }

    /** The reel screen is still open (another check): re-evaluate, because a lock or window may have started. */
    @Synchronized
    fun onReelScreenSeen(): BlockAction {
        val now = clock.sample()
        refresh(now)
        gate = gate?.seen(now.elapsedMs) ?: GateSession(now.elapsedMs)
        return decide(now)
    }

    /** The reel screen is gone. The gate session ends only after a short grace, so a quick back-and-forth keeps it. */
    @Synchronized
    fun onReelScreenClosed() {
        // Nothing to clear now: GateSession.hasEnded() decides on the next open, using the last time it was seen.
    }

    /** "Dekhna hai". Accepted only after the 10 seconds. */
    @Synchronized
    fun continueGate(): Boolean {
        val now = clock.sample()
        val next = gate?.continued(now.elapsedMs) ?: return false
        gate = next
        return true
    }

    // ----- friend unlock --------------------------------------------------------------------------

    /** A friend request reached the server just now. It counts towards the 3 per 24 hours, and until it is answered or expires it blocks a second ask. */
    @Synchronized
    fun onUnlockRequested(id: String) {
        val atMs = clock.sample().calendarMs
        state = state.copy(
            unlockRequestTimesMs = (state.unlockRequestTimesMs + atMs).takeLast(MAX_KEPT_REQUESTS),
            pendingUnlock = PendingUnlock(id, atMs),
        )
        store.save(state)
    }

    /** The friend answered (approved or denied), or it expired. Clears the waiting request. */
    @Synchronized
    fun onUnlockResolved(id: String) {
        if (state.pendingUnlock?.id != id) return
        state = state.copy(pendingUnlock = null)
        store.save(state)
    }

    /**
     * The friend approved. [passLengthMs] is what is left of the 15 minutes (see
     * [UnlockCoordinator.passLengthOnArrival]). Returns false if nothing is left or friend unlock is off.
     */
    @Synchronized
    fun onFriendApproved(id: String, passLengthMs: Long): Boolean {
        val now = clock.sample()
        refresh(now)
        state = state.copy(pendingUnlock = state.pendingUnlock?.takeIf { it.id != id })
        if (passLengthMs <= 0 || !settingsSource.current().canAskFriend) {
            store.save(state)
            return false
        }
        state = state.copy(pass = FriendPass.start(passLengthMs.coerceAtMost(UnlockRules.PASS_LENGTH.toMillis()), now))
        store.save(state)
        return true
    }

    // ----- settings -------------------------------------------------------------------------------

    /** Applies a change from the Settings screen, unless the commitment rule refuses it. */
    @Synchronized
    fun change(change: SettingChange): ChangeResult {
        val now = clock.sample()
        refresh(now)
        val settings = settingsSource.current()
        val zone = zones.zone()
        val window = TimeWindows.active(settings.bedtime, settings.focus, instant(now), zone)
        val today = DayKeys.dateOf(now.calendarMs, zone)
        val result = SettingsPolicy.apply(settings, change, state.lock, window, today, now.calendarMs)
        if (result is ChangeResult.Applied) settingsSource.save(result.settings)
        return result
    }

    // ----- internals ------------------------------------------------------------------------------

    /** Advances lock, pass and the pending request to [now]; saves when one of them ended. */
    private fun refresh(now: ClockSample) {
        val zone = zones.zone()
        val lock = StrictLock.advance(state.lock, now, zone)
        val pass = state.pass?.advance(now)
        val pending = state.pendingUnlock?.takeIf { now.calendarMs < it.requestedAtMs + UnlockRules.REQUEST_TTL.toMillis() }

        val structural = (state.lock is StrictLockState.Locked) != (lock is StrictLockState.Locked) ||
            (state.pass != null) != (pass != null) ||
            (state.pendingUnlock != null) != (pending != null) ||
            (lock is StrictLockState.Idle && state.lock is StrictLockState.Idle && lock != state.lock)
        state = state.copy(lock = lock, pass = pass, pendingUnlock = pending)
        if (structural) store.save(state)
    }

    private fun decide(now: ClockSample): BlockAction {
        val settings = settingsSource.current()
        val zone = zones.zone()
        val at = instant(now)
        val lockDecision = (state.lock as? StrictLockState.Locked)?.let { LockDecision.Block(it.remaining) } ?: LockDecision.Allow
        val times = state.unlockRequestTimesMs.map { Instant.ofEpochMilli(it) }
        return BlockingEngine.decide(
            BlockingInput(
                settings = settings,
                lock = lockDecision,
                pass = state.pass,
                window = TimeWindows.active(settings.bedtime, settings.focus, at, zone),
                gate = gate,
                nowElapsedMs = now.elapsedMs,
                nowMs = now.calendarMs,
                askQuotaLeft = UnlockCoordinator.remaining(times, at),
                askPending = state.pendingUnlock != null,
            ),
        )
    }

    private fun statusAt(now: ClockSample): BlockingStatus {
        val settings = settingsSource.current()
        val at = instant(now)
        val times = state.unlockRequestTimesMs.map { Instant.ofEpochMilli(it) }
        return BlockingStatus(
            settings = settings,
            lock = state.lock,
            pass = state.pass,
            window = TimeWindows.active(settings.bedtime, settings.focus, at, zones.zone()),
            askQuotaLeft = UnlockCoordinator.remaining(times, at),
            askPending = state.pendingUnlock != null,
            nowMs = now.calendarMs,
        )
    }

    private fun instant(now: ClockSample) = Instant.ofEpochMilli(now.calendarMs)

    companion object {
        const val MIN_WAKE_MS = 1_000L
        const val MAX_WAKE_MS = 24 * 3_600_000L
        private const val MAX_KEPT_REQUESTS = 10
    }
}
