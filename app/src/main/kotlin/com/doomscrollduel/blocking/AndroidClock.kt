package com.doomscrollduel.blocking

import android.content.ContentResolver
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.doomscrollduel.domain.blocking.ClockSample
import com.doomscrollduel.domain.blocking.ClockSource
import com.doomscrollduel.domain.blocking.ZoneSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A calendar time from our server that the user cannot move. We remember "the server said X when the monotonic
 * clock read Y" and carry it forward with the monotonic clock, for as long as the phone has not rebooted.
 * Every answer from the unlock functions refreshes it. Without network the anchor just gets older or is absent,
 * and the lock falls back to the monotonic clock (same boot) or the phone clock (after a reboot).
 */
@Singleton
class TrustedTime @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("trusted_time", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver

    /** Call with the server's current time whenever we hear it. */
    fun update(serverNowMs: Long) {
        prefs.edit()
            .putLong(KEY_SERVER, serverNowMs)
            .putLong(KEY_ELAPSED, SystemClock.elapsedRealtime())
            .putString(KEY_BOOT, bootId(resolver))
            .apply()
    }

    /** The trusted time now, or null if there is no anchor from this boot. */
    fun nowMsOrNull(): Long? {
        val boot = prefs.getString(KEY_BOOT, null) ?: return null
        if (boot != bootId(resolver)) return null
        val elapsedThen = prefs.getLong(KEY_ELAPSED, -1L)
        val nowElapsed = SystemClock.elapsedRealtime()
        if (elapsedThen < 0 || nowElapsed < elapsedThen) return null
        return prefs.getLong(KEY_SERVER, 0L) + (nowElapsed - elapsedThen)
    }

    private companion object {
        const val KEY_SERVER = "server_ms"
        const val KEY_ELAPSED = "elapsed_ms"
        const val KEY_BOOT = "boot"
    }
}

/** Changes at every reboot (`Settings.Global.BOOT_COUNT`). */
fun bootId(resolver: ContentResolver): String =
    runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT).toString() }.getOrDefault("unknown")

@Singleton
class AndroidClockSource @Inject constructor(
    @ApplicationContext context: Context,
    private val trusted: TrustedTime,
) : ClockSource {
    private val resolver = context.contentResolver

    override fun sample() = ClockSample(
        wallMs = System.currentTimeMillis(),
        elapsedMs = SystemClock.elapsedRealtime(),
        bootId = bootId(resolver),
        trustedMs = trusted.nowMsOrNull(),
    )
}

class SystemZoneSource @Inject constructor() : ZoneSource {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
