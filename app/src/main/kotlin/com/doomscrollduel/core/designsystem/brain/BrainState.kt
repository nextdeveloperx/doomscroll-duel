package com.doomscrollduel.core.designsystem.brain

enum class BrainState {
    HAPPY,
    FRIED,
    ZOMBIE;

    companion object {
        /**
         * Up to 40 percent HAPPY, 41 to 99 percent FRIED, 100 percent or more ZOMBIE.
         * [percentUsed] is a whole number, see [ReelUsage.percentUsed].
         */
        fun fromPercentUsed(percentUsed: Int): BrainState = when {
            percentUsed >= 100 -> ZOMBIE
            percentUsed > 40 -> FRIED
            else -> HAPPY
        }
    }
}

/** Whose brain it is. The opponent's happy brain is cyan; fried and zombie colours are shared. */
enum class BrainOwner { YOU, OPPONENT }

/** The reel-limit maths that drives [BrainState] and the HP bar. Pure Kotlin. */
object ReelUsage {
    /**
     * Whole percent of the limit used, rounded down, so 99.9 percent is still 99 (FRIED) and only a
     * fully used limit is ZOMBIE. A limit of zero or less counts as fully used once anything is watched.
     */
    fun percentUsed(used: Int, limit: Int): Int {
        if (limit <= 0) return if (used > 0) 100 else 0
        return ((used.coerceAtLeast(0).toLong() * 100L) / limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** HP is 100 minus the percent used, never below 0. */
    fun hpForPercentUsed(percentUsed: Int): Int = (100 - percentUsed).coerceIn(0, 100)

    fun hp(used: Int, limit: Int): Int = hpForPercentUsed(percentUsed(used, limit))

    fun brainState(used: Int, limit: Int): BrainState = BrainState.fromPercentUsed(percentUsed(used, limit))
}
