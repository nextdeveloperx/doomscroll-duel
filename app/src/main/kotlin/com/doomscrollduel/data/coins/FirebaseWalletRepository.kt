package com.doomscrollduel.data.coins

import android.util.Log
import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.CoinOp
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.duel.Duel
import com.doomscrollduel.domain.challenge.duel.DuelConfig
import com.doomscrollduel.domain.challenge.duel.DuelDuration
import com.doomscrollduel.domain.challenge.duel.DuelSettlement
import com.doomscrollduel.domain.challenge.duel.DuelState
import com.doomscrollduel.domain.challenge.duel.PlayerScore
import com.doomscrollduel.domain.coins.MilestoneCreditResult
import com.doomscrollduel.domain.coins.ReelMilestoneRules
import com.doomscrollduel.domain.coins.WalletRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** See [WalletRepository]. */
@Singleton
class FirebaseWalletRepository @Inject constructor() : WalletRepository {
    private val db get() = FirebaseFirestore.getInstance()

    override suspend fun holdStake(duelId: String, me: String, stakeCoins: Int) {
        writeOp(me, "$duelId:hold:$me", "hold", stakeCoins, duelId)
    }

    override suspend fun settle(duelId: String, creator: String, opponent: String, creatorReels: Int, opponentReels: Int, stakeCoins: Int) {
        // Reuses the same pure, tested settlement logic the Cloud Function would run. Forfeits are never recognised
        // this way (the tracking gaps that decide one are not written to Firestore), so a forfeited duel settles as
        // a tie here — same limitation as the Firestore rules that check this write, see wallets/{uid}/ops.
        val duel = Duel(
            id = ChallengeId(duelId),
            mode = ChallengeMode.DUEL,
            creator = PlayerId(creator),
            opponent = PlayerId(opponent),
            config = DuelConfig(reelLimit = DuelConfig.MIN_LIMIT, duration = DuelDuration.SIX_HOURS, stake = stakeCoins),
            state = DuelState.Active(Instant.EPOCH, Instant.EPOCH),
        )
        val result = DuelSettlement.settle(
            creator = PlayerScore(PlayerId(creator), creatorReels),
            opponent = PlayerScore(PlayerId(opponent), opponentReels),
            startedAt = Instant.EPOCH,
            endsAt = Instant.EPOCH,
        )
        DuelSettlement.payoutOps(duel, result).forEach { op ->
            when (op) {
                is CoinOp.Release -> writeOp(op.player.value, op.id, "release", op.amount, duelId)
                is CoinOp.Transfer -> {
                    writeOp(op.to.value, op.id, "transfer_credit", op.amount, duelId)
                    writeOp(op.from.value, op.id, "transfer_debit", op.amount, duelId)
                }
                is CoinOp.Hold -> Unit // not part of payout
            }
        }
    }

    private suspend fun writeOp(uid: String, opId: String, kind: String, amount: Int, duelId: String) {
        try {
            db.collection("wallets").document(uid).collection("ops").document(opId).set(
                mapOf("kind" to kind, "amount" to amount, "duelId" to duelId, "at" to FieldValue.serverTimestamp()),
            ).await()
        } catch (e: FirebaseFirestoreException) {
            // Expected most of the time: this op is not mine to write, or it was already applied by the other
            // player's phone (or an earlier try of my own). Anything else is worth a log line.
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) Log.w(TAG, "wallet op $opId failed: ${e.code}")
        }
    }

    override suspend fun creditReelMilestone(me: String, milestoneIndex: Int): MilestoneCreditResult {
        require(milestoneIndex > 0) { "milestoneIndex must be positive, was $milestoneIndex" }
        val opId = "reel_milestone:$milestoneIndex"
        return try {
            db.collection("wallets").document(me).collection("ops").document(opId).set(
                mapOf("kind" to "reel_milestone", "amount" to ReelMilestoneRules.coinsFor(milestoneIndex), "milestone" to milestoneIndex, "at" to FieldValue.serverTimestamp()),
            ).await()
            MilestoneCreditResult.CREDITED
        } catch (e: FirebaseFirestoreException) {
            // A create-only op under rules that forbid update: PERMISSION_DENIED on a retry overwhelmingly means
            // this exact milestone already exists (the common "already applied" case writeOp's comment describes
            // above). Anything else (offline, a transient server error, ...) is a real failure to retry later —
            // never silently treated as success, per the "no false success state" requirement.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                MilestoneCreditResult.ALREADY_CREDITED
            } else {
                Log.w(TAG, "reel milestone op $opId failed: ${e.code}")
                MilestoneCreditResult.FAILED
            }
        }
    }

    private companion object {
        const val TAG = "WalletRepository"
    }
}
