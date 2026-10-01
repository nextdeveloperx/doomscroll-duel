package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.PendingCap
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What must survive the app being killed and the phone rebooting: the lock timer, a friend's pass, and the
 * friend requests made in the last 24 hours. Saved whenever one of them changes.
 *
 * Progress of a running timer is NOT saved on every tick, and does not need to be: it is worked out from the last
 * saved reading plus the real time that has passed since (see [LockClock]), so a stale reading loses nothing.
 */
data class BlockingState(
    val lock: StrictLockState = StrictLockState.Idle(LocalDate.EPOCH),
    val pass: FriendPass? = null,
    /** Times (epoch ms, server-or-phone clock at request time) of friend requests in the last 24 hours. */
    val unlockRequestTimesMs: List<Long> = emptyList(),
    /** The request waiting for an answer, if any. */
    val pendingUnlock: PendingUnlock? = null,
)

data class PendingUnlock(val id: String, val requestedAtMs: Long)

// ----- JSON on disk ------------------------------------------------------------------------------

@Serializable
private data class SampleDto(val wall: Long, val elapsed: Long, val boot: String? = null, val trusted: Long? = null)

@Serializable
private data class LockDto(
    val type: String,
    val day: String,
    val baseline: Int = 0,
    val lockedAtWallMs: Long = 0,
    val totalMs: Long = 0,
    val progressMs: Long = 0,
    val last: SampleDto? = null,
    val totalAtLock: Int = 0,
)

@Serializable
private data class PassDto(val remainingMs: Long, val last: SampleDto)

@Serializable
private data class PendingDto(val id: String, val at: Long)

@Serializable
private data class StateDto(
    val schema: Int = 1,
    val lock: LockDto,
    val pass: PassDto? = null,
    val requests: List<Long> = emptyList(),
    val pending: PendingDto? = null,
)

@Serializable
private data class RangeDto(val start: String, val end: String)

@Serializable
private data class PendingLimitDto(val cap: Int, val from: String)

@Serializable
private data class SettingsDto(
    val schema: Int = 1,
    val strictLock: Boolean = false,
    /** "midnight" or "3h". */
    val lockLength: String = "midnight",
    val dailyLimit: Int = BlockingSettings.DEFAULT_LIMIT,
    val pendingLimit: PendingLimitDto? = null,
    val friendUnlock: Boolean = false,
    val buddyUid: String? = null,
    val buddyName: String? = null,
    val wait10: Boolean = false,
    val bedtimeOn: Boolean = false,
    val bedtime: RangeDto = RangeDto("23:00", "06:00"),
    val focusOn: Boolean = false,
    /** ISO day number (Monday = 1) to ranges. */
    val focus: Map<Int, List<RangeDto>> = emptyMap(),
)

/** Reads and writes the two JSON documents kept in DataStore. */
object BlockingCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ----- state

    fun encodeState(state: BlockingState): String = json.encodeToString(StateDto.serializer(), state.toDto())

    /** Returns [BlockingState] defaults when the text is empty or damaged, rather than crashing the service. */
    fun decodeState(text: String?): BlockingState =
        if (text.isNullOrBlank()) BlockingState()
        else runCatching { json.decodeFromString(StateDto.serializer(), text).toState() }.getOrElse { BlockingState() }

    private fun ClockSample.toDto() = SampleDto(wallMs, elapsedMs, bootId, trustedMs)
    private fun SampleDto.toSample() = ClockSample(wall, elapsed, boot, trusted)

    private fun BlockingState.toDto(): StateDto {
        val lockDto = when (val l = lock) {
            is StrictLockState.Idle -> LockDto("idle", l.day.toString(), baseline = l.baseline)
            is StrictLockState.Locked -> LockDto(
                "locked", l.day.toString(), lockedAtWallMs = l.lockedAtWallMs, totalMs = l.totalMs,
                progressMs = l.progressMs, last = l.lastSample.toDto(), totalAtLock = l.totalAtLock,
            )
        }
        return StateDto(
            lock = lockDto,
            pass = pass?.let { PassDto(it.remainingMs, it.lastSample.toDto()) },
            requests = unlockRequestTimesMs,
            pending = pendingUnlock?.let { PendingDto(it.id, it.requestedAtMs) },
        )
    }

    private fun StateDto.toState(): BlockingState {
        val lockState = when {
            lock.type == "locked" && lock.last != null -> StrictLockState.Locked(
                day = LocalDate.parse(lock.day), lockedAtWallMs = lock.lockedAtWallMs, totalMs = lock.totalMs,
                progressMs = lock.progressMs, lastSample = lock.last.toSample(), totalAtLock = lock.totalAtLock,
            )
            else -> StrictLockState.Idle(LocalDate.parse(lock.day), lock.baseline)
        }
        return BlockingState(
            lock = lockState,
            pass = pass?.let { FriendPass(it.remainingMs, it.last.toSample()) },
            unlockRequestTimesMs = requests,
            pendingUnlock = pending?.let { PendingUnlock(it.id, it.at) },
        )
    }

    // ----- settings

    fun encodeSettings(settings: BlockingSettings): String = json.encodeToString(SettingsDto.serializer(), settings.toDto())

    fun decodeSettings(text: String?): BlockingSettings =
        if (text.isNullOrBlank()) BlockingSettings()
        else runCatching { json.decodeFromString(SettingsDto.serializer(), text).toSettings() }.getOrElse { BlockingSettings() }

    private fun TimeRange.toDto() = RangeDto(start.toString(), end.toString())
    private fun RangeDto.toRange() = TimeRange(LocalTime.parse(start), LocalTime.parse(end))

    private fun BlockingSettings.toDto() = SettingsDto(
        strictLock = strictLockEnabled,
        lockLength = when (val l = lockLength) {
            LockLength.UntilMidnight -> "midnight"
            is LockLength.Hours -> "${l.hours}h"
        },
        dailyLimit = dailyLimit,
        pendingLimit = pendingLimit?.let { PendingLimitDto(it.cap, it.from.toString()) },
        friendUnlock = friendUnlockEnabled,
        buddyUid = buddy?.uid?.value,
        buddyName = buddy?.displayName,
        wait10 = wait10Enabled,
        bedtimeOn = bedtime.enabled,
        bedtime = bedtime.range.toDto(),
        focusOn = focus.enabled,
        focus = focus.days.entries.associate { (day, ranges) -> day.value to ranges.map { it.toDto() } },
    )

    private fun SettingsDto.toSettings() = BlockingSettings(
        strictLockEnabled = strictLock,
        lockLength = if (lockLength.endsWith("h")) LockLength.Hours(lockLength.dropLast(1).toInt()) else LockLength.UntilMidnight,
        dailyLimit = dailyLimit,
        pendingLimit = pendingLimit?.let { PendingCap(it.cap, LocalDate.parse(it.from)) },
        friendUnlockEnabled = friendUnlock,
        buddy = if (buddyUid != null && buddyName != null) Buddy(PlayerId(buddyUid), buddyName) else null,
        wait10Enabled = wait10,
        bedtime = BedtimeSettings(bedtimeOn, bedtime.toRange()),
        focus = FocusSettings(focusOn, focus.entries.associate { (d, r) -> DayOfWeek.of(d) to r.map { it.toRange() } }),
    )
}
