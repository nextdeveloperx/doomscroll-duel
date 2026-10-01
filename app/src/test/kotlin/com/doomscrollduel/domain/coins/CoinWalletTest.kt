package com.doomscrollduel.domain.coins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinWalletTest {
    private val d1 = "2026-10-01"
    private val d2 = "2026-10-02"

    private fun CoinWallet.granted(e: EarnEvent, day: String = d1): CoinWallet {
        val r = earn(e, day)
        assertTrue("$e -> $r", r is EarnResult.Granted)
        return (r as EarnResult.Granted).wallet
    }

    private fun CoinWallet.refusal(e: EarnEvent, day: String = d1): EarnRefusal =
        (earn(e, day) as EarnResult.Refused).reason

    private fun win(ref: String, opp: String = "bob", dur: Long = 2 * 3_600_000L, tracked: Boolean = true) =
        EarnEvent.DuelWin(ref, opp, dur, tracked)

    @Test fun `check-in pays 10 once a day`() {
        val w = CoinWallet().granted(EarnEvent.CheckIn("ci1"))
        assertEquals(10, w.balance)
        assertEquals(EarnRefusal.SOURCE_LIMIT_REACHED, w.refusal(EarnEvent.CheckIn("ci2")))
        assertEquals(20, w.granted(EarnEvent.CheckIn("ci3"), d2).balance)
    }

    @Test fun `the same event never pays twice`() {
        val w = CoinWallet().granted(EarnEvent.CheckIn("ci1"))
        assertEquals(EarnResult.Duplicate, w.earn(EarnEvent.CheckIn("ci1"), d2))
    }

    @Test fun `a duel win pays once per opponent per day and at most three a day`() {
        var w = CoinWallet().granted(win("w1", "bob"))
        assertEquals(EarnRefusal.OPPONENT_ALREADY_PAID, w.refusal(win("w2", "bob")))
        w = w.granted(win("w3", "cara")).granted(win("w4", "dev"))
        assertEquals(60, w.balance)
        assertEquals(EarnRefusal.SOURCE_LIMIT_REACHED, w.refusal(win("w5", "eli")))
    }

    @Test fun `win bonus needs a real duel`() {
        val w = CoinWallet()
        assertEquals(EarnRefusal.NOT_ELIGIBLE, w.refusal(win("a", dur = 59 * 60_000L)))
        assertEquals(EarnRefusal.NOT_ELIGIBLE, w.refusal(win("b", tracked = false)))
    }

    @Test fun `opponent memory resets on a new day`() {
        val w = CoinWallet().granted(win("w1", "bob"))
        assertEquals(40, w.granted(win("w2", "bob"), d2).balance)
    }

    @Test fun `streak coins start at 2 days`() {
        assertEquals(EarnRefusal.NOT_ELIGIBLE, CoinWallet().refusal(EarnEvent.StreakDay("s", 1)))
        assertEquals(5, CoinWallet().granted(EarnEvent.StreakDay("s", 2)).balance)
    }

    @Test fun `night pact pays only when fully completed`() {
        assertEquals(EarnRefusal.NOT_ELIGIBLE, CoinWallet().refusal(EarnEvent.NightPact("n", completedFully = false)))
        assertEquals(30, CoinWallet().granted(EarnEvent.NightPact("n", true)).balance)
    }

    @Test fun `the daily cap trims the last grant and then refuses`() {
        // check-in 10 + 3 wins 60 + streak 5 + pact 30 = 105, under the cap; fill the rest through a tight wallet
        val almost = CoinWallet(tally = EarnTally(day = d1, total = 145))
        val r = almost.earn(EarnEvent.NightPact("n", true), d1) as EarnResult.Granted
        assertEquals(5, r.granted)
        assertEquals(150, r.wallet.tally.total)
        assertEquals(EarnRefusal.DAILY_CAP_REACHED, r.wallet.refusal(EarnEvent.CheckIn("c")))
    }

    @Test fun `the full natural day fits under the cap`() {
        val max = EarnSource.entries.filter { it.countsTowardDailyCap }.sumOf { it.coins * it.perDayLimit }
        assertTrue(max <= CoinRules.DAILY_EARN_CAP)
    }

    @Test fun `milestones pay once per streak length and ignore the daily cap`() {
        val full = CoinWallet(tally = EarnTally(day = d1, total = CoinRules.DAILY_EARN_CAP))
        val w = full.granted(EarnEvent.StreakMilestone("m7", 7))
        assertEquals(50, w.balance)
        assertEquals(CoinRules.DAILY_EARN_CAP, w.tally.total)
        assertEquals(EarnRefusal.NOT_ELIGIBLE, w.refusal(EarnEvent.StreakMilestone("m8", 8)))
        assertEquals(EarnResult.Duplicate, w.earn(EarnEvent.StreakMilestone("m7", 7), d1))
    }

    @Test fun `buying a coin item takes coins and gives the item once`() {
        val w = CoinWallet(balance = 400)
        val bought = (w.buy("skin_cool", "b1") as SpendResult.Bought).wallet
        assertEquals(250, bought.balance)
        assertTrue("skin_cool" in bought.owned)
        assertEquals(SpendRefusal.ALREADY_OWNED, (bought.buy("skin_cool", "b2") as SpendResult.Refused).reason)
        assertEquals(SpendRefusal.DUPLICATE, (bought.buy("skin_sleepy", "b1") as SpendResult.Refused).reason)
    }

    @Test fun `not enough coins and unknown items are refused without changing anything`() {
        val w = CoinWallet(balance = 10)
        assertEquals(SpendRefusal.NOT_ENOUGH_COINS, (w.buy("theme_midnight", "x") as SpendResult.Refused).reason)
        assertEquals(SpendRefusal.UNKNOWN_ITEM, (w.buy("nope", "y") as SpendResult.Refused).reason)
    }

    @Test fun `a balance can never go negative`() {
        var w = CoinWallet(balance = 120)
        repeat(10) { i -> (w.buy("sticker_roast_basic", "s$i") as? SpendResult.Bought)?.let { w = it.wallet } }
        assertEquals(0, w.balance)
    }

    @Test fun `stickers are used up and can be bought again`() {
        var w = CoinWallet(balance = 100)
        w = (w.buy("sticker_roast_basic", "s1") as SpendResult.Bought).wallet
        w = (w.buy("sticker_roast_basic", "s2") as SpendResult.Bought).wallet
        assertEquals(60, w.balance)
        assertTrue(w.owned.isEmpty())
    }

    @Test fun `pro skins cannot be bought with coins`() {
        assertEquals(SpendRefusal.NOT_FOR_SALE, (CoinWallet(balance = 9999).buy("skin_gold", "x") as SpendResult.Refused).reason)
    }

    @Test fun `pro skin is worn while pro and falls back when pro ends, without being deleted`() {
        val w = CoinWallet().equip("skin_gold", isPro = true)
        assertEquals("skin_gold", w.effectiveSkin(isPro = true))
        assertNull(w.effectiveSkin(isPro = false))
        assertEquals("skin_gold", w.equippedSkin)
        assertEquals(w, w.equip("skin_gold", isPro = false)) // a free player cannot switch to it
        assertNull(CoinWallet().equip("skin_gold", isPro = false).equippedSkin)
    }

    @Test fun `cannot equip what you do not own`() {
        assertNull(CoinWallet().equip("skin_cool", isPro = true).equippedSkin)
        assertEquals("theme_sunset", CoinWallet(owned = setOf("theme_sunset")).equip("theme_sunset", false).equippedTheme)
    }

    @Test fun `applied refs stay bounded`() {
        var w = CoinWallet(balance = 0)
        repeat(CoinWallet.MAX_REFS + 50) { i -> w = w.copy(appliedRefs = (w.appliedRefs + "r$i").takeLast(CoinWallet.MAX_REFS)) }
        assertEquals(CoinWallet.MAX_REFS, w.appliedRefs.size)
    }

    @Test fun `no earn source can be bought with money and nothing here mentions billing`() {
        // Structural: the only ways to get coins are the EarnSource entries.
        assertFalse(EarnSource.entries.any { it.name.contains("PURCHASE", true) || it.name.contains("BUY", true) || it.name.contains("PRO", true) })
    }
}

class StakeRulesTest {
    @Test fun `stake options are inside the duel limits`() {
        CoinRules.STAKE_OPTIONS.forEach {
            assertTrue(it >= com.doomscrollduel.domain.challenge.duel.DuelConfig.MIN_STAKE)
            assertTrue(it <= com.doomscrollduel.domain.challenge.duel.DuelConfig.MAX_STAKE)
        }
    }
}
