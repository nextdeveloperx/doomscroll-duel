package com.doomscrollduel.domain.billing

import com.doomscrollduel.domain.challenge.ChallengeMode

/** Everything Pro unlocks. The free plan is everything not listed here. */
enum class ProFeature {
    UNLIMITED_DUELS,
    SQUAD_BATTLE,
    STRICT_LOCK,
    ANALYTICS,
    CUSTOM_SCHEDULES,
    BRAIN_SKINS,
}

sealed interface Access {
    data object Allowed : Access
    data class NeedsPro(val feature: ProFeature) : Access
}

object FreeLimits {
    /** One-on-one duels a free player can START per local day. Accepting someone else's duel is always free. */
    const val DUELS_PER_DAY = 3

    /**
     * Only duels that stay in the quota: pending or running or finished. A duel that was cancelled or expired
     * before the friend accepted does not burn one.
     */
    fun canStartDuel(countedToday: Int, isPro: Boolean): Access =
        if (isPro || countedToday < DUELS_PER_DAY) Access.Allowed else Access.NeedsPro(ProFeature.UNLIMITED_DUELS)

    fun duelsLeft(countedToday: Int, isPro: Boolean): Int? =
        if (isPro) null else (DUELS_PER_DAY - countedToday).coerceAtLeast(0)

    fun check(feature: ProFeature, isPro: Boolean): Access = if (isPro) Access.Allowed else Access.NeedsPro(feature)

    /** The feature a challenge mode needs, or null when it is free. Agrees with [com.doomscrollduel.domain.challenge.ProGate]. */
    fun featureFor(mode: ChallengeMode): ProFeature? = when (mode) {
        ChallengeMode.SQUAD -> ProFeature.SQUAD_BATTLE
        ChallengeMode.STRICT_LOCK -> ProFeature.STRICT_LOCK
        ChallengeMode.DUEL, ChallengeMode.NIGHT_PACT, ChallengeMode.FORFEIT_DARE -> null
    }
}
