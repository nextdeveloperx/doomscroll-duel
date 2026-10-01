package com.doomscrollduel.domain.blocking

/** How the overlay and Settings print a time left. Pure, so it is tested. */
object TimeFormat {
    /** "1:23:45" from an hour up, otherwise "23:45". Rounds UP, so "0:01" is shown until the very end, never "0:00" early. */
    fun clock(remainingMs: Long): String {
        val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** "1 ghanta 5 min" style in Hinglish for sentences: hours and minutes only, rounded up to the minute. */
    fun words(remainingMs: Long): String {
        val minutes = ((remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h ghante $m min"
            h > 0 -> "$h ghante"
            else -> "$m min"
        }
    }

    /** "11:00 PM", "6:30 AM", "12:00 AM". */
    fun time12(time: java.time.LocalTime): String {
        val h = time.hour
        val suffix = if (h < 12) "AM" else "PM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(hour12, time.minute, suffix)
    }
}
