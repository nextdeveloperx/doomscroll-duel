package com.doomscrollduel.domain.coins

import com.doomscrollduel.domain.social.LeaderboardRepository

/**
 * Pure (no Android, no Hilt) credit-and-notify loop for reel-milestone rewards: 1 coin for every 10 valid reels
 * ever counted, on top of — never instead of — duel winnings. See [ReelMilestoneRules] for the milestone
 * arithmetic and [WalletRepository.creditReelMilestone] for exactly what duplicate-safety guarantee backs this.
 * [com.doomscrollduel.data.coins.ReelMilestoneRewarder] is the thin, Hilt-injected wrapper that actually runs this
 * against a live reel total on an app-lifetime [kotlinx.coroutines.CoroutineScope] and wires [onMilestonesCredited]
 * to a system Toast; this class holds the actual logic and is what tests exercise directly.
 *
 * Duplicate safety: each milestone's Firestore operation id is derived purely from its own index
 * (`reel_milestone:{N}`, see [WalletRepository.creditReelMilestone] and firestore.rules' `isReelMilestone`), and
 * Firestore only ever lets a given document id be *created* once — so a retry, an app restart mid-credit, two
 * devices signed into the same account, or this very loop re-running from an earlier point can never pay the same
 * milestone twice. [progress] (per-uid "last milestone attempted") is only a local optimisation to avoid
 * re-walking the whole history on every change; it is not what prevents a double credit — the
 * [MilestoneCreditResult] from the actual Firestore write is (see [MilestoneProgressStore]).
 */
class ReelMilestoneCreditor(
    private val wallet: WalletRepository,
    private val progress: MilestoneProgressStore,
    private val leaderboard: LeaderboardRepository,
    private val onMilestonesCredited: suspend (creditedMilestones: List<Int>) -> Unit,
) {
    /** Credits every milestone owed between the last one we know we reached and [totalReels], in order, stopping
     * at the first failure so nothing is skipped and no false success is reported. */
    suspend fun creditUpTo(uid: String, totalReels: Int) {
        val start = progress.lastCredited(uid)
        val eligible = ReelMilestoneRules.newlyEligible(start, totalReels)
        if (eligible.isEmpty()) return
        val creditedNow = mutableListOf<Int>()
        for (milestone in eligible) {
            when (wallet.creditReelMilestone(uid, milestone)) {
                MilestoneCreditResult.CREDITED -> {
                    progress.setLastCredited(uid, milestone)
                    creditedNow += milestone
                }
                MilestoneCreditResult.ALREADY_CREDITED -> progress.setLastCredited(uid, milestone) // resolved; don't retry it
                MilestoneCreditResult.FAILED -> break // offline / retryable: stop here, the next tick tries again
            }
        }
        if (creditedNow.isNotEmpty()) {
            leaderboard.refreshMyScore()
            onMilestonesCredited(creditedNow)
        }
    }
}
