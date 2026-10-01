package com.doomscrollduel.domain.coins

/** One day's earnings, so the daily cap and per-source limits can be checked. A new day starts a new tally. */
data class EarnTally(
    val day: String = "",
    val total: Int = 0,
    val counts: Map<EarnSource, Int> = emptyMap(),
    /** Opponents already paid a duel-win bonus today. */
    val paidOpponents: Set<String> = emptySet(),
) {
    fun countOf(source: EarnSource): Int = counts[source] ?: 0
}

/** What happened that earns coins. [refId] makes it idempotent: the same event can never pay twice. */
sealed interface EarnEvent {
    val source: EarnSource
    val refId: String

    data class CheckIn(override val refId: String) : EarnEvent { override val source = EarnSource.DAILY_CHECK_IN }

    data class DuelWin(
        override val refId: String,
        val opponent: String,
        val durationMs: Long,
        /** False when the opponent never counted a single reel (they did not play) or tracking was off. */
        val bothTracked: Boolean,
    ) : EarnEvent { override val source = EarnSource.DUEL_WIN }

    data class StreakDay(override val refId: String, val streakDays: Int) : EarnEvent { override val source = EarnSource.STREAK_DAY }

    data class NightPact(override val refId: String, val completedFully: Boolean) : EarnEvent { override val source = EarnSource.NIGHT_PACT }

    data class StreakMilestone(override val refId: String, val streakDays: Int) : EarnEvent { override val source = EarnSource.STREAK_MILESTONE }
}

enum class EarnRefusal {
    NOT_ELIGIBLE,
    SOURCE_LIMIT_REACHED,
    DAILY_CAP_REACHED,
    OPPONENT_ALREADY_PAID,
}

sealed interface EarnResult {
    /** [granted] can be less than the table amount when the daily cap only had room for part of it. */
    data class Granted(val wallet: CoinWallet, val granted: Int) : EarnResult
    data object Duplicate : EarnResult
    data class Refused(val reason: EarnRefusal) : EarnResult
}

enum class SpendRefusal { UNKNOWN_ITEM, ALREADY_OWNED, NOT_ENOUGH_COINS, NEEDS_PRO, NOT_FOR_SALE, DUPLICATE }

sealed interface SpendResult {
    data class Bought(val wallet: CoinWallet) : SpendResult
    data class Refused(val reason: SpendRefusal) : SpendResult
}

/**
 * A player's coins, what they own and what they earned today. Pure: every function returns a new wallet.
 * The SERVER keeps the real one; this class is the shared rule set and the app's offline copy for showing numbers.
 */
data class CoinWallet(
    val balance: Int = 0,
    val tally: EarnTally = EarnTally(),
    val owned: Set<String> = emptySet(),
    val equippedSkin: String? = null,
    val equippedTheme: String? = null,
    /** Ids already applied, oldest first. Bounded so the wallet stays small. */
    val appliedRefs: List<String> = emptyList(),
) {
    fun earn(event: EarnEvent, day: String): EarnResult {
        if (event.refId in appliedRefs) return EarnResult.Duplicate
        val today = if (tally.day == day) tally else EarnTally(day = day)
        val source = event.source
        val milestone = event is EarnEvent.StreakMilestone

        val base = when (event) {
            is EarnEvent.CheckIn -> source.coins
            is EarnEvent.DuelWin -> {
                if (!event.bothTracked || event.durationMs < CoinRules.DUEL_WIN_MIN_DURATION_MS) return refused(EarnRefusal.NOT_ELIGIBLE)
                if (event.opponent in today.paidOpponents) return refused(EarnRefusal.OPPONENT_ALREADY_PAID)
                source.coins
            }
            is EarnEvent.StreakDay -> {
                if (event.streakDays < CoinRules.STREAK_DAY_MIN) return refused(EarnRefusal.NOT_ELIGIBLE)
                source.coins
            }
            is EarnEvent.NightPact -> {
                if (!event.completedFully) return refused(EarnRefusal.NOT_ELIGIBLE)
                source.coins
            }
            is EarnEvent.StreakMilestone -> CoinRules.milestoneCoins(event.streakDays) ?: return refused(EarnRefusal.NOT_ELIGIBLE)
        }

        if (today.countOf(source) >= source.perDayLimit) return refused(EarnRefusal.SOURCE_LIMIT_REACHED)

        val room = if (milestone) base else CoinRules.DAILY_EARN_CAP - today.total
        if (room <= 0) return refused(EarnRefusal.DAILY_CAP_REACHED)
        val granted = minOf(base, room)

        val newTally = today.copy(
            total = today.total + if (source.countsTowardDailyCap) granted else 0,
            counts = today.counts + (source to today.countOf(source) + 1),
            paidOpponents = if (event is EarnEvent.DuelWin) today.paidOpponents + event.opponent else today.paidOpponents,
        )
        return EarnResult.Granted(copy(balance = balance + granted, tally = newTally, appliedRefs = remember(event.refId)), granted)
    }

    /**
     * Buys [item]. Coins are destroyed (a sink). A Pro item is not for sale: it is free to wear while Pro, see
     * [effectiveSkin]. A consumable (sticker) is paid for on every use and not stored.
     */
    fun buy(itemId: String, refId: String): SpendResult {
        if (refId in appliedRefs) return SpendResult.Refused(SpendRefusal.DUPLICATE)
        val item = CoinRules.item(itemId) ?: return SpendResult.Refused(SpendRefusal.UNKNOWN_ITEM)
        if (item.access == ItemAccess.PRO) return SpendResult.Refused(SpendRefusal.NOT_FOR_SALE)
        if (!item.consumable && item.id in owned) return SpendResult.Refused(SpendRefusal.ALREADY_OWNED)
        if (balance < item.price) return SpendResult.Refused(SpendRefusal.NOT_ENOUGH_COINS)
        return SpendResult.Bought(
            copy(
                balance = balance - item.price,
                owned = if (item.consumable) owned else owned + item.id,
                appliedRefs = remember(refId),
            ),
        )
    }

    /** Wears a skin or theme the player owns, or any Pro skin while [isPro]. Returns this wallet when not allowed. */
    fun equip(itemId: String, isPro: Boolean): CoinWallet {
        val item = CoinRules.item(itemId) ?: return this
        val allowed = when (item.access) {
            ItemAccess.COINS -> item.id in owned
            ItemAccess.PRO -> isPro
        }
        if (!allowed) return this
        return when (item.kind) {
            ItemKind.BRAIN_SKIN -> copy(equippedSkin = itemId)
            ItemKind.THEME -> copy(equippedTheme = itemId)
            ItemKind.ROAST_STICKER -> this
        }
    }

    /** The skin to draw: a Pro skin falls back to the default (null) when Pro has ended. Nothing is deleted. */
    fun effectiveSkin(isPro: Boolean): String? {
        val id = equippedSkin ?: return null
        val item = CoinRules.item(id) ?: return null
        return if (item.access == ItemAccess.PRO && !isPro) null else id
    }

    private fun remember(refId: String): List<String> = (appliedRefs + refId).takeLast(MAX_REFS)

    private fun refused(reason: EarnRefusal) = EarnResult.Refused(reason)

    companion object {
        const val MAX_REFS = 500
    }
}
