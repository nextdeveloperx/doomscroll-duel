package com.doomscrollduel.domain.coins

/**
 * Where [com.doomscrollduel.data.coins.ReelMilestoneRewarder] remembers, per signed-in user, the highest reel
 * milestone it has already attempted to credit. Purely a local optimisation so it does not re-walk the whole
 * milestone history (from 1) on every reel or on every retry tick — duplicate-credit safety itself comes from
 * Firestore's create-only operation ids (see `isReelMilestone` in firestore.rules), not from this store. Losing
 * this value (a fresh install, cleared app data, ...) is harmless: the rewarder simply re-attempts every milestone
 * from 1, and every one already credited on the server is reported back as [MilestoneCreditResult.ALREADY_CREDITED]
 * rather than paid again.
 */
interface MilestoneProgressStore {
    fun lastCredited(uid: String): Int
    fun setLastCredited(uid: String, milestone: Int)
}
