package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.CoinOp
import com.doomscrollduel.domain.challenge.ForfeitRule
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.TrackingGap
import java.time.Instant

/** One player's numbers for the whole duel window: reels counted and the gaps in counting. */
data class PlayerScore(
    val player: PlayerId,
    val reels: Int,
    val gaps: List<TrackingGap> = emptyList(),
) {
    init {
        require(reels >= 0) { "reels cannot be negative" }
    }
}

/**
 * Settlement as pure functions: same inputs, same answer, no clock, no database. This is what the
 * Cloud Function runs, and what the phone runs to show the same result offline.
 *
 * Decision order:
 *  1. The 10 minute rule. A player who had the accessibility service off for more than 10 minutes in a
 *     row inside the window forfeits at the instant they crossed it. If one player forfeits, the other
 *     wins. If both did, whoever crossed first forfeits and the other wins. Crossing at the very same
 *     instant is a mutual forfeit.
 *  2. Otherwise the LOWER reel count wins, even when both are over the limit.
 *  3. Equal counts are a tie.
 *
 * The limit does not change the winner: with one shared limit, a player over it always has a higher count
 * than a player under it. The limit only drives the brain, HP and warnings.
 */
object DuelSettlement {

    fun settle(creator: PlayerScore, opponent: PlayerScore, startedAt: Instant, endsAt: Instant): DuelResult {
        val creatorForfeitAt = ForfeitRule.forfeitMoment(creator.gaps, startedAt, endsAt)
        val opponentForfeitAt = ForfeitRule.forfeitMoment(opponent.gaps, startedAt, endsAt)

        if (creatorForfeitAt != null || opponentForfeitAt != null) {
            return when {
                // Only the opponent forfeited: the creator wins. And the other way round.
                creatorForfeitAt == null -> win(creator, opponent, WinReason.OPPONENT_FORFEIT)
                opponentForfeitAt == null -> win(opponent, creator, WinReason.OPPONENT_FORFEIT)
                creatorForfeitAt.isBefore(opponentForfeitAt) -> win(opponent, creator, WinReason.OPPONENT_FORFEIT)
                opponentForfeitAt.isBefore(creatorForfeitAt) -> win(creator, opponent, WinReason.OPPONENT_FORFEIT)
                else -> DuelResult.MutualForfeit
            }
        }
        return when {
            creator.reels < opponent.reels -> win(creator, opponent, WinReason.LOWER_COUNT)
            opponent.reels < creator.reels -> win(opponent, creator, WinReason.LOWER_COUNT)
            else -> DuelResult.Tie(creator.reels)
        }
    }

    private fun win(winner: PlayerScore, loser: PlayerScore, reason: WinReason) =
        DuelResult.Win(winner.player, loser.player, winner.reels, loser.reels, reason)

    /** True when the result ends the duel as FORFEITED rather than FINISHED. */
    fun isForfeit(result: DuelResult): Boolean = when (result) {
        is DuelResult.Win -> result.reason == WinReason.OPPONENT_FORFEIT
        is DuelResult.MutualForfeit -> true
        is DuelResult.Tie -> false
    }

    /** Coin movements that pay out [result]. Ids are stable per duel, so replaying them is harmless. */
    fun payoutOps(duel: Duel, result: DuelResult): List<CoinOp> {
        val stake = duel.config.stake
        if (stake == 0) return emptyList()
        return when (result) {
            is DuelResult.Win -> listOf(
                // The loser's escrowed stake goes to the winner; the winner's own stake is released.
                CoinOp.Transfer("${duel.id}:settle:transfer", from = result.loser, to = result.winner, amount = stake),
                CoinOp.Release("${duel.id}:settle:release:${result.winner}", result.winner, stake),
            )
            is DuelResult.Tie, DuelResult.MutualForfeit -> refundOps(duel, "settle")
        }
    }

    /** Hold both stakes. Done once, when the invite is accepted. */
    fun holdOps(duel: Duel): List<CoinOp> {
        val stake = duel.config.stake
        if (stake == 0) return emptyList()
        return listOf(
            CoinOp.Hold("${duel.id}:hold:${duel.creator}", duel.creator, stake),
            CoinOp.Hold("${duel.id}:hold:${duel.opponent}", duel.opponent, stake),
        )
    }

    private fun refundOps(duel: Duel, tag: String): List<CoinOp> = listOf(
        CoinOp.Release("${duel.id}:$tag:release:${duel.creator}", duel.creator, duel.config.stake),
        CoinOp.Release("${duel.id}:$tag:release:${duel.opponent}", duel.opponent, duel.config.stake),
    )
}
