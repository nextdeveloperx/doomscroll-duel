package com.doomscrollduel.domain.coins

/** What happened when [WalletRepository.creditReelMilestone] tried to write one milestone's coin. */
enum class MilestoneCreditResult {
    /** A brand-new credit: this milestone had never been written before. Safe to show success feedback for. */
    CREDITED,

    /** The write was refused — almost always because this exact milestone was already credited (by an earlier
     * try, another device signed into the same account, or a race with this same device). Treated as resolved:
     * the caller should not retry it and should not show success feedback again for it. */
    ALREADY_CREDITED,

    /** A retryable problem (offline, a transient server error, ...). Nothing was credited. The caller must not
     * advance past this milestone or show success feedback — it should simply try again later. */
    FAILED,
}

/**
 * Moves real coins without a Cloud Function: see `wallets/{uid}/ops` in firestore.rules, which mirror
 * [com.doomscrollduel.domain.challenge.duel.DuelSettlement]'s hold/payout ops for duels, and a separate
 * `reel_milestone` op kind for the reel-milestone reward (1 coin per 10 valid reels, see
 * [com.doomscrollduel.domain.coins.ReelMilestoneRules]). Safe to call from either player's phone, any number of
 * times: every op can be written once only, and a write that is not this uid's to make is simply refused.
 */
interface WalletRepository {
    /** Escrows my own stake once a duel I'm in becomes active. */
    suspend fun holdStake(duelId: String, me: String, stakeCoins: Int)

    /** Pays out (or, on a tie, refunds) a finished duel from its two players' final reel counts. */
    suspend fun settle(duelId: String, creator: String, opponent: String, creatorReels: Int, opponentReels: Int, stakeCoins: Int)

    /**
     * Credits exactly 1 coin for reaching reel milestone [milestoneIndex] (every [ReelMilestoneRules.REELS_PER_COIN]
     * valid reels = one milestone). A completely separate operation from duel coins: its own op kind, its own id
     * namespace (`reel_milestone:{milestoneIndex}`, never a duel id), never touches a duel's escrow or settlement.
     * Idempotent by construction: the milestone's operation id can be created once only, so calling this again for
     * a milestone already credited — from a retry, an app restart, or another device on the same account — can
     * never pay it twice; see [MilestoneCreditResult.ALREADY_CREDITED]. Trust tier: WEAKER than duel coins, which
     * are cross-checked against both players' counts — a reel milestone is self-reported with nothing on the
     * server to verify the claimed reel count against, since this deployment has no Cloud Functions and reel
     * counts are not otherwise uploaded outside of an active duel. firestore.rules can still guarantee each
     * milestone pays out once only, exactly 1 coin, to its rightful owner, and only in ascending order (see
     * `isReelMilestone`) — it cannot guarantee the claimed reel count was genuinely reached.
     */
    suspend fun creditReelMilestone(me: String, milestoneIndex: Int): MilestoneCreditResult
}
