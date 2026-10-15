package com.doomscrollduel.domain.coins

import com.doomscrollduel.domain.social.LeaderboardBoard
import com.doomscrollduel.domain.social.LeaderboardRepository
import com.doomscrollduel.domain.social.LeaderboardUi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records every call and lets a test script FAILED/ALREADY_CREDITED for specific milestones. */
private class FakeWallet : WalletRepository {
    val credited = mutableListOf<Pair<String, Int>>() // (uid, milestone), in call order
    var alreadyCredited: Set<Int> = emptySet()
    var failAt: Set<Int> = emptySet()

    override suspend fun holdStake(duelId: String, me: String, stakeCoins: Int) = Unit
    override suspend fun settle(duelId: String, creator: String, opponent: String, creatorReels: Int, opponentReels: Int, stakeCoins: Int) = Unit

    override suspend fun creditReelMilestone(me: String, milestoneIndex: Int): MilestoneCreditResult {
        if (milestoneIndex in failAt) return MilestoneCreditResult.FAILED
        if (milestoneIndex in alreadyCredited) return MilestoneCreditResult.ALREADY_CREDITED
        credited += me to milestoneIndex
        return MilestoneCreditResult.CREDITED
    }
}

private class FakeProgress : MilestoneProgressStore {
    private val state = mutableMapOf<String, Int>()
    override fun lastCredited(uid: String): Int = state[uid] ?: 0
    override fun setLastCredited(uid: String, milestone: Int) { state[uid] = milestone }
}

private class FakeLeaderboard : LeaderboardRepository {
    var refreshCalls = 0
    override fun board(board: LeaderboardBoard) = flowOf<LeaderboardUi>(LeaderboardUi.Loading)
    override suspend fun refreshMyScore() { refreshCalls++ }
}

class ReelMilestoneCreditorTest {
    private fun creditor(
        wallet: FakeWallet = FakeWallet(),
        progress: FakeProgress = FakeProgress(),
        leaderboard: FakeLeaderboard = FakeLeaderboard(),
        onCredited: MutableList<List<Int>> = mutableListOf(),
    ) = ReelMilestoneCreditor(wallet, progress, leaderboard) { onCredited += it }

    @Test
    fun `9 reels earns nothing`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 9)
        assertEquals(emptyList<Pair<String, Int>>(), wallet.credited)
    }

    @Test
    fun `10 reels credits exactly milestone 1`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 10)
        assertEquals(listOf("alice" to 1), wallet.credited)
    }

    @Test
    fun `19 reels still credits only 1 cumulative coin`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 19)
        assertEquals(listOf("alice" to 1), wallet.credited)
    }

    @Test
    fun `20 reels credits 2 cumulative coins`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 20)
        assertEquals(listOf("alice" to 1, "alice" to 2), wallet.credited)
    }

    @Test
    fun `27 reels credits 2 cumulative coins and preserves remainder 7`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 27)
        assertEquals(listOf("alice" to 1, "alice" to 2), wallet.credited)
        assertEquals(7, ReelMilestoneRules.remainderFor(27))
    }

    @Test
    fun `30 reels credits 3 cumulative coins`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 30)
        assertEquals(listOf("alice" to 1, "alice" to 2, "alice" to 3), wallet.credited)
    }

    @Test
    fun `100 reels credits 10 cumulative coins`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 100)
        assertEquals((1..10).map { "alice" to it }, wallet.credited)
    }

    @Test
    fun `1000 reels credits 100 cumulative coins`() = runTest {
        val wallet = FakeWallet()
        creditor(wallet).creditUpTo("alice", 1000)
        assertEquals((1..100).map { "alice" to it }, wallet.credited)
    }

    @Test
    fun `reprocessing the same count does not award duplicate coins`() = runTest {
        val wallet = FakeWallet()
        val progress = FakeProgress()
        val c = creditor(wallet, progress)
        c.creditUpTo("alice", 27)
        c.creditUpTo("alice", 27) // same total again
        assertEquals(listOf("alice" to 1, "alice" to 2), wallet.credited) // no duplicates
    }

    @Test
    fun `restarting the app (fresh progress store) does not re-pay a milestone the server already has`() = runTest {
        // Simulates local state being lost: the server still rejects the duplicate, so no extra coin is paid,
        // and local progress catches back up from the server's answer.
        val wallet = FakeWallet()
        wallet.alreadyCredited = setOf(1, 2)
        val freshProgress = FakeProgress() // "app restart": local progress forgotten
        creditor(wallet, freshProgress).creditUpTo("alice", 27)
        assertEquals(emptyList<Pair<String, Int>>(), wallet.credited) // nothing newly paid
        assertEquals(2, freshProgress.lastCredited("alice")) // caught back up locally
    }

    @Test
    fun `a jump from 19 to 45 credits milestones 2, 3 and 4 exactly once each`() = runTest {
        val wallet = FakeWallet()
        val progress = FakeProgress()
        progress.setLastCredited("alice", 1) // milestone 1 (reels 1-19) already accounted for
        creditor(wallet, progress).creditUpTo("alice", 45)
        assertEquals(listOf("alice" to 2, "alice" to 3, "alice" to 4), wallet.credited)
        assertEquals(5, ReelMilestoneRules.remainderFor(45))
    }

    @Test
    fun `a failed transaction does not create a false success state`() = runTest {
        val wallet = FakeWallet()
        wallet.failAt = setOf(3) // milestone 3 (at 30 reels) fails, e.g. offline
        val progress = FakeProgress()
        val credited = mutableListOf<List<Int>>()
        creditor(wallet, progress, onCredited = credited).creditUpTo("alice", 45)
        // 1 and 2 succeed, 3 fails and stops the batch -- 4 is never attempted even though 45 reels would allow it.
        assertEquals(listOf("alice" to 1, "alice" to 2), wallet.credited)
        assertEquals(2, progress.lastCredited("alice")) // progress not advanced past the failure
        assertEquals(listOf(listOf(1, 2)), credited) // only the genuine successes were ever reported
    }

    @Test
    fun `a later retry resumes from the failure point without re-crediting earlier milestones`() = runTest {
        val wallet = FakeWallet()
        wallet.failAt = setOf(3)
        val progress = FakeProgress()
        val c = creditor(wallet, progress)
        c.creditUpTo("alice", 45) // 1, 2 credited; 3 fails
        wallet.failAt = emptySet() // the network recovers
        c.creditUpTo("alice", 45) // retried tick, same total
        assertEquals(listOf("alice" to 1, "alice" to 2, "alice" to 3, "alice" to 4), wallet.credited)
        assertEquals(4, progress.lastCredited("alice"))
    }

    @Test
    fun `an older or repeated total cannot reverse progress or create extra rewards`() = runTest {
        val wallet = FakeWallet()
        val progress = FakeProgress()
        val c = creditor(wallet, progress)
        c.creditUpTo("alice", 45) // reaches milestone 4
        c.creditUpTo("alice", 30) // a stale/older update arrives afterwards
        assertEquals((1..4).map { "alice" to it }, wallet.credited) // nothing extra, nothing reversed
        assertEquals(4, progress.lastCredited("alice"))
    }

    @Test
    fun `two uids never share milestone progress`() = runTest {
        val wallet = FakeWallet()
        val progress = FakeProgress()
        val c = creditor(wallet, progress)
        c.creditUpTo("alice", 20)
        c.creditUpTo("bob", 10)
        assertEquals(listOf("alice" to 1, "alice" to 2, "bob" to 1), wallet.credited)
        assertEquals(2, progress.lastCredited("alice"))
        assertEquals(1, progress.lastCredited("bob"))
    }

    @Test
    fun `concurrent callers racing the same uid never double-credit (server is the real guard)`() = runTest {
        // Two "concurrent" attempts to credit the same milestone from a fresh/unsynced local progress store each
        // (e.g. two devices on one account): the wallet fake plays the role of the server, which only ever lets
        // each opId be created once. Neither caller double-pays even though neither knew about the other.
        val wallet = FakeWallet()
        val deviceA = creditor(wallet, FakeProgress())
        val deviceB = creditor(wallet, FakeProgress())
        deviceA.creditUpTo("alice", 10)
        assertEquals(listOf("alice" to 1), wallet.credited)
        wallet.alreadyCredited = setOf(1) // what the server would now report to device B
        deviceB.creditUpTo("alice", 10)
        assertEquals(listOf("alice" to 1), wallet.credited) // still just the one credit, total
    }

    @Test
    fun `leaderboard is refreshed only when a coin was actually credited`() = runTest {
        val wallet = FakeWallet()
        val leaderboard = FakeLeaderboard()
        creditor(wallet, leaderboard = leaderboard).creditUpTo("alice", 9) // no milestone crossed
        assertEquals(0, leaderboard.refreshCalls)
        creditor(wallet, leaderboard = leaderboard).creditUpTo("alice", 10)
        assertEquals(1, leaderboard.refreshCalls)
    }

    @Test
    fun `no feedback is shown when nothing new was credited`() = runTest {
        val wallet = FakeWallet()
        val progress = FakeProgress()
        progress.setLastCredited("alice", 1)
        val shown = mutableListOf<List<Int>>()
        creditor(wallet, progress, onCredited = shown).creditUpTo("alice", 15) // still within milestone 1
        assertEquals(emptyList<List<Int>>(), shown)
    }
}
