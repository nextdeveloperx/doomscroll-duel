package com.doomscrollduel.domain.blocking

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

fun wall(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0, s: Int = 0): Long =
    ZonedDateTime.of(y, m, d, h, min, s, 0, zone).toInstant().toEpochMilli()

fun sample(wallMs: Long, elapsedMs: Long, boot: String? = "boot-1", trusted: Long? = null) = ClockSample(wallMs, elapsedMs, boot, trusted)

/** A clock the tests move by hand: real time passing, and the user tampering with the phone clock. */
class FakeClock(
    var wallMs: Long,
    var elapsedMs: Long = 1_000_000L,
    var boot: String? = "boot-1",
    var trustedMs: Long? = null,
) : ClockSource {
    override fun sample() = ClockSample(wallMs, elapsedMs, boot, trustedMs)

    /** Real time passes: both clocks move (and the trusted one, if we have it). */
    fun pass(ms: Long) {
        wallMs += ms
        elapsedMs += ms
        trustedMs = trustedMs?.plus(ms)
    }

    /** The user moves the phone's calendar clock. Real time does not change. */
    fun userSetsClock(deltaMs: Long) {
        wallMs += deltaMs
    }

    /** The phone reboots after [offMs] of real time: elapsed restarts, the boot id changes, trusted time is lost. */
    fun reboot(offMs: Long) {
        wallMs += offMs
        elapsedMs = 5_000L
        boot = "boot-" + ((boot ?: "boot-0").substringAfter('-').toIntOrNull()?.plus(1) ?: 1)
        trustedMs = null
    }
}

class FakeZone(var zone: ZoneId = IST) : ZoneSource {
    override fun zone() = zone
}

class FakeSettings(var settings: BlockingSettings = BlockingSettings()) : BlockingSettingsSource {
    var saves = 0
    override fun current() = settings
    override fun save(settings: BlockingSettings) {
        this.settings = settings
        saves++
    }
}

class FakeStore(var saved: BlockingState = BlockingState()) : BlockingStateStore {
    var saves = 0
    override fun load() = saved
    override fun save(state: BlockingState) {
        saved = state
        saves++
    }
}

fun hoursMs(h: Long) = h * 3_600_000L
fun minutesMs(m: Long) = m * 60_000L
fun inst(ms: Long): Instant = Instant.ofEpochMilli(ms)
