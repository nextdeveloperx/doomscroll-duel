package com.doomscrollduel.domain.challenge

/** The five challenge modes. Matches `BattleMode` in the UI layer one to one. */
enum class ChallengeMode(val isPro: Boolean, val usesCoins: Boolean) {
    /** Two players, lower reel count wins the escrowed coin stake. */
    DUEL(isPro = false, usesCoins = true),

    /** Two squads of 3 to 10, lower average reels per member wins. No coins. */
    SQUAD(isPro = true, usesCoins = false),

    /** Friends agree on zero reels 11 PM to 6 AM. No coins. */
    NIGHT_PACT(isPro = false, usesCoins = false),

    /** A duel with no stake: the loser does a dare the winner picks. */
    FORFEIT_DARE(isPro = false, usesCoins = false),

    /** Personal daily cap that locks the reel screens. */
    STRICT_LOCK(isPro = true, usesCoins = false),
}

/**
 * Pro is needed to CREATE a Pro mode (the squad leader, the person setting a Strict Lock).
 * Joining a squad is always free, so a Pro user can bring friends in.
 */
object ProGate {
    fun canCreate(mode: ChallengeMode, isPro: Boolean): Boolean = !mode.isPro || isPro

    fun canJoin(@Suppress("UNUSED_PARAMETER") mode: ChallengeMode): Boolean = true
}

/** How long an invite, squad lobby or pact invite stays open. */
const val INVITE_TTL_HOURS = 24L

/** Lifecycle shared by duels and squad battles. */
enum class DuelStatus(val isTerminal: Boolean) {
    PENDING(false),
    ACTIVE(false),
    FINISHED(true),
    FORFEITED(true),
    CANCELLED(true),
    EXPIRED(true),
}
