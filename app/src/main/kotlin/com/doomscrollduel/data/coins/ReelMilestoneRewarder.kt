package com.doomscrollduel.data.coins

import android.content.Context
import android.widget.Toast
import com.doomscrollduel.R
import com.doomscrollduel.domain.coins.MilestoneProgressStore
import com.doomscrollduel.domain.coins.ReelMilestoneCreditor
import com.doomscrollduel.domain.coins.ReelMilestoneRules
import com.doomscrollduel.domain.coins.WalletRepository
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.domain.social.LeaderboardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [ReelMilestoneCreditor] for as long as the app process does, started from [com.doomscrollduel.DuelApp] the
 * same way as [com.doomscrollduel.data.social.DuelTracker]. Reacts to the lifetime reel total
 * ([ReelRepository.observeLifetimeTotal], a count that is never pruned, unlike the day-by-day history the daily
 * limit uses) rather than being threaded through the accessibility event pipeline — so reel detection, the daily
 * limit, and the floating counter are completely untouched by this feature. A periodic tick (same idea as
 * [com.doomscrollduel.data.social.DuelTracker]'s ticker) also retries any milestone that failed to credit while
 * offline, even if no new reel lands in the meantime.
 *
 * All the actual credit/duplicate-safety/notify logic lives in [ReelMilestoneCreditor], which has no Android or
 * Hilt dependency and is what the unit tests exercise directly; this class only owns the live coroutine collection
 * and turns a successful credit into a system [Toast] (reaches the person even while they're inside
 * Instagram/YouTube/etc., which is where this fires most of the time, and — unlike a dialog — never interrupts
 * reel viewing).
 */
@Singleton
class ReelMilestoneRewarder @Inject constructor(
    private val auth: AuthRepository,
    private val reels: ReelRepository,
    wallet: WalletRepository,
    leaderboard: LeaderboardRepository,
    progress: MilestoneProgressStore,
    @ApplicationContext private val context: Context,
) {
    private val creditor = ReelMilestoneCreditor(wallet, progress, leaderboard) { milestones ->
        val coins = milestones.size
        val reelsNow = milestones.last() * ReelMilestoneRules.REELS_PER_COIN
        val message = if (coins == 1) {
            context.getString(R.string.reel_milestone_toast_single, reelsNow)
        } else {
            context.getString(R.string.reel_milestone_toast_multi, coins, reelsNow)
        }
        withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            val ticker = flow {
                while (true) {
                    emit(Unit)
                    delay(RETRY_TICK_MS)
                }
            }
            combine(auth.session, reels.observeLifetimeTotal().distinctUntilChanged(), ticker) { session, total, _ ->
                session?.uid to total
            }.collectLatest { (uid, total) -> if (uid != null) creditor.creditUpTo(uid, total) }
        }
    }

    private companion object {
        const val RETRY_TICK_MS = 20_000L
    }
}
