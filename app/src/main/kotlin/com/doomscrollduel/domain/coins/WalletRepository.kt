package com.doomscrollduel.domain.coins

/**
 * Moves real coins for a duel's stake without a Cloud Function: see `wallets/{uid}/ops` in firestore.rules, which
 * mirror [com.doomscrollduel.domain.challenge.duel.DuelSettlement]'s hold/payout ops exactly. Safe to call from
 * either player's phone, any number of times: every op can be written once only, and a write that is not this
 * uid's to make is simply refused.
 */
interface WalletRepository {
    /** Escrows my own stake once a duel I'm in becomes active. */
    suspend fun holdStake(duelId: String, me: String, stakeCoins: Int)

    /** Pays out (or, on a tie, refunds) a finished duel from its two players' final reel counts. */
    suspend fun settle(duelId: String, creator: String, opponent: String, creatorReels: Int, opponentReels: Int, stakeCoins: Int)
}
