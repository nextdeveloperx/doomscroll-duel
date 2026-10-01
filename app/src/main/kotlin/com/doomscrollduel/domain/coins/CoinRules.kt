package com.doomscrollduel.domain.coins

/**
 * THE COIN RULES. Coins are virtual. They are earned by playing, spent in the app, and moved between friends as duel
 * stakes. They are NEVER sold for money, never given by a subscription, and never paid out.
 * `functions/src/coin-rules.json` is the server's copy of this table; CoinRulesParityTest fails when they differ.
 */
enum class EarnSource(val id: String, val coins: Int, val perDayLimit: Int, val countsTowardDailyCap: Boolean) {
    /** Open the app and tap check-in. */
    DAILY_CHECK_IN("daily_check_in", coins = 10, perDayLimit = 1, countsTowardDailyCap = true),

    /** Win a duel. The loser's stake moves to you as well; this is the small extra for winning. */
    DUEL_WIN("duel_win", coins = 20, perDayLimit = 3, countsTowardDailyCap = true),

    /** Stay under your daily reel limit, 2 days in a row or more. One per day. */
    STREAK_DAY("streak_day", coins = 5, perDayLimit = 1, countsTowardDailyCap = true),

    /** Finish a Night Pact: zero reels from start to end. One per day. */
    NIGHT_PACT("night_pact", coins = 30, perDayLimit = 1, countsTowardDailyCap = true),

    /** A streak reaches 7, 30 or 100 days. Paid once per milestone per streak run; the amount depends on the day. */
    STREAK_MILESTONE("streak_milestone", coins = 0, perDayLimit = 1, countsTowardDailyCap = false),
}

enum class ItemKind { BRAIN_SKIN, THEME, ROAST_STICKER }

/** How an item is obtained: [COINS] anyone can buy it with coins; [PRO] it comes with Pro and cannot be bought. */
enum class ItemAccess { COINS, PRO }

data class ShopItem(
    val id: String,
    val kind: ItemKind,
    val access: ItemAccess,
    /** Coins. For a ROAST_STICKER this is the price of one send, because stickers are used up. */
    val price: Int,
) {
    val consumable: Boolean get() = kind == ItemKind.ROAST_STICKER
}

object CoinRules {
    /** The most coins a person can EARN in one local day (stake transfers and milestones are not earnings). */
    const val DAILY_EARN_CAP = 150

    /** Duel stakes a player can pick. Held in escrow and moved to the winner, so the total never changes. */
    val STAKE_OPTIONS = listOf(25, 50, 100)

    /** A duel win pays the bonus once per opponent per day, so two friends cannot trade wins to farm. */
    const val DUEL_WIN_ONCE_PER_OPPONENT_PER_DAY = true

    /** A duel shorter than this does not pay the win bonus. */
    const val DUEL_WIN_MIN_DURATION_MS = 60L * 60 * 1000

    /** Streak lengths that pay a one-off bonus, and how much. */
    val STREAK_MILESTONES = mapOf(7 to 50, 30 to 200, 100 to 500)

    /** Minimum streak for the daily streak coins. */
    const val STREAK_DAY_MIN = 2

    /** Starting balance for a new player, so the first duel is possible. Given once, by the server. */
    const val WELCOME_COINS = 50

    val CATALOG: List<ShopItem> = listOf(
        ShopItem("skin_cool", ItemKind.BRAIN_SKIN, ItemAccess.COINS, 150),
        ShopItem("skin_sleepy", ItemKind.BRAIN_SKIN, ItemAccess.COINS, 250),
        ShopItem("skin_gold", ItemKind.BRAIN_SKIN, ItemAccess.PRO, 0),
        ShopItem("skin_neon", ItemKind.BRAIN_SKIN, ItemAccess.PRO, 0),
        ShopItem("theme_sunset", ItemKind.THEME, ItemAccess.COINS, 200),
        ShopItem("theme_midnight", ItemKind.THEME, ItemAccess.COINS, 400),
        ShopItem("sticker_roast_basic", ItemKind.ROAST_STICKER, ItemAccess.COINS, 20),
        ShopItem("sticker_roast_spicy", ItemKind.ROAST_STICKER, ItemAccess.COINS, 50),
    )

    fun item(id: String): ShopItem? = CATALOG.firstOrNull { it.id == id }

    fun milestoneCoins(streakDays: Int): Int? = STREAK_MILESTONES[streakDays]
}
