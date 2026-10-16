package com.doomscrollduel.domain.coins

/**
 * Every 10 valid reels ever counted (lifetime, no daily cap) pay a coin reward — pure arithmetic, no I/O. Coins are whole
 * numbers, so "1.5 coins per 10 reels" is paid as 1 coin for the odd milestones and 2 for the even ones (see [coinsFor]):
 * after 10 reels 1 coin, after 20 reels 3, after 30 reels 4, after 40 reels 6, and so on. A "milestone" is
 * identified by its own index: milestone N is reached once the lifetime total is >= N * [REELS_PER_COIN]. Given the
 * highest milestone already credited and the current lifetime total, [newlyEligible] lists every milestone that
 * still needs crediting — usually one, but a total that jumps by more than ten in a single update (an offline
 * catch-up, say) can cross several at once; all of them are owed, in order, exactly once each.
 */
object ReelMilestoneRules {
    const val REELS_PER_COIN = 10

    /** The coins milestone [milestone] pays: 1 for the odd ones, 2 for the even ones, so two milestones (20 reels) pay 3 and 10 reels average 1.5. */
    fun coinsFor(milestone: Int): Int = if (milestone % 2 == 0) 2 else 1

    /** All the coins milestones 1..[milestones] have paid together (1, 3, 4, 6, 7, 9, ...). */
    fun totalCoinsFor(milestones: Int): Int = milestones.coerceAtLeast(0).let { it + it / 2 }

    /** Highest milestone index reached by [totalReels] (0 if fewer than [REELS_PER_COIN] reels so far). */
    fun milestoneFor(totalReels: Int): Int = (totalReels.coerceAtLeast(0)) / REELS_PER_COIN

    /** Reels still needed before the next coin, given [totalReels] (always 0..9). */
    fun remainderFor(totalReels: Int): Int = totalReels.coerceAtLeast(0) % REELS_PER_COIN

    /**
     * Every milestone index from (lastCredited + 1) up to [milestoneFor] of [totalReels], in ascending order —
     * empty if nothing new is owed. Never includes a milestone at or below [lastCredited], so re-running this with
     * the same or a stale (lower) [totalReels] never re-lists an already-credited milestone.
     */
    fun newlyEligible(lastCredited: Int, totalReels: Int): List<Int> {
        val target = milestoneFor(totalReels)
        val from = lastCredited.coerceAtLeast(0) + 1
        if (target < from) return emptyList()
        return (from..target).toList()
    }
}
