package com.doomscrollduel.domain.analytics

import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.billing.ProPlan

/**
 * The nine analytics events, and nothing else. An event carries only SHORT LOWERCASE TOKENS chosen from fixed lists or
 * from the buckets below: never a name, username, id, email, phone number, count, time stamp or any text a person typed.
 * `docs/analytics-events.md` is the written spec; `AnalyticsSpecTest` fails when the two differ.
 */
object AnalyticsSpec {
    const val MAX_VALUE_LENGTH = 40
    const val MAX_PARAMS = 8

    /** Event name -> the only parameter names it may carry. */
    val EVENTS: Map<String, Set<String>> = mapOf(
        "onboarding_completed" to setOf("steps_skipped"),
        "permission_granted" to setOf("permission"),
        "duel_created" to setOf("mode", "duration_hours", "entry_coins", "limit_bucket"),
        "duel_accepted" to setOf("mode", "accept_delay"),
        "duel_finished" to setOf("mode", "result", "reels_bucket", "over_limit"),
        "lock_triggered" to setOf("kind", "length"),
        "unlock_requested" to setOf("result"),
        "paywall_viewed" to setOf("trigger"),
        "subscription_started" to setOf("plan"),
    )

    /** The only user property: free or pro. */
    const val USER_PROPERTY_PLAN = "plan"
}

/** Fixed buckets. A bucket hides the exact number on purpose. */
object Buckets {
    fun reels(count: Int): String = when {
        count <= 0 -> "0"
        count <= 20 -> "1_20"
        count <= 50 -> "21_50"
        count <= 100 -> "51_100"
        count <= 200 -> "101_200"
        else -> "201_plus"
    }

    fun entryCoins(coins: Int): String = when (coins) {
        0 -> "0"
        25, 50, 100 -> coins.toString()
        else -> "other"
    }

    fun durationHours(hours: Int): String = when (hours) {
        6, 24, 168 -> hours.toString()
        else -> "other"
    }

    fun acceptDelay(hours: Long): String = when {
        hours < 1 -> "lt_1h"
        hours < 6 -> "1_6h"
        hours < 24 -> "6_24h"
        else -> "gt_24h"
    }

    fun stepsSkipped(count: Int): String = when {
        count <= 0 -> "0"
        count <= 2 -> "1_2"
        else -> "3_plus"
    }
}

enum class AnalyticsMode(val token: String) { DUEL("duel"), SQUAD("squad"), NIGHT_PACT("night_pact"), FORFEIT_DARE("forfeit_dare"), STRICT_LOCK("strict_lock") }

enum class AnalyticsPermission(val token: String) { ACCESSIBILITY("accessibility"), NOTIFICATIONS("notifications"), BATTERY("battery_unrestricted") }

enum class DuelResultToken(val token: String) { WIN("win"), LOSS("loss"), DRAW("draw"), FORFEIT_WIN("forfeit_win"), FORFEIT_LOSS("forfeit_loss"), CANCELLED("cancelled"), EXPIRED("expired") }

enum class LockKind(val token: String) { TIMER("timer"), BEDTIME("bedtime"), FOCUS("focus") }

enum class UnlockResultToken(val token: String) { SENT("sent"), QUOTA_USED("quota_used"), ALREADY_PENDING("already_pending"), NO_BUDDY("no_buddy"), NO_NETWORK("no_network"), NOT_SIGNED_IN("not_signed_in"), FAILED("failed") }

abstract class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {
    /** The first-run flow ended. [skipped] is how many optional steps the person skipped. */
    class OnboardingCompleted(skipped: Int) : AnalyticsEvent("onboarding_completed", mapOf("steps_skipped" to Buckets.stepsSkipped(skipped)))

    class PermissionGranted(permission: AnalyticsPermission) : AnalyticsEvent("permission_granted", mapOf("permission" to permission.token))

    class DuelCreated(mode: AnalyticsMode, durationHours: Int, entryCoins: Int, reelLimit: Int) : AnalyticsEvent(
        "duel_created",
        mapOf(
            "mode" to mode.token,
            "duration_hours" to Buckets.durationHours(durationHours),
            "entry_coins" to Buckets.entryCoins(entryCoins),
            "limit_bucket" to Buckets.reels(reelLimit),
        ),
    )

    class DuelAccepted(mode: AnalyticsMode, hoursSinceInvite: Long) : AnalyticsEvent(
        "duel_accepted",
        mapOf("mode" to mode.token, "accept_delay" to Buckets.acceptDelay(hoursSinceInvite)),
    )

    class DuelFinished(mode: AnalyticsMode, result: DuelResultToken, myReels: Int, overLimit: Boolean) : AnalyticsEvent(
        "duel_finished",
        mapOf(
            "mode" to mode.token,
            "result" to result.token,
            "reels_bucket" to Buckets.reels(myReels),
            "over_limit" to overLimit.toString(),
        ),
    )

    /** A lock actually stopped the person (the timer-lock started, or a bedtime or focus window blocked a reel screen). */
    class LockTriggered(kind: LockKind, lengthHours: Int? = null) : AnalyticsEvent(
        "lock_triggered",
        buildMap {
            put("kind", kind.token)
            if (kind == LockKind.TIMER) put("length", lengthHours?.let { "${it}h" } ?: "midnight")
        },
    )

    class UnlockRequested(result: UnlockResultToken) : AnalyticsEvent("unlock_requested", mapOf("result" to result.token))

    /** [trigger] is the locked thing the person tapped, or null when they opened the paywall directly. */
    class PaywallViewed(trigger: ProFeature?) : AnalyticsEvent("paywall_viewed", mapOf("trigger" to (trigger?.name?.lowercase() ?: "direct")))

    class SubscriptionStarted(plan: ProPlan) : AnalyticsEvent("subscription_started", mapOf("plan" to plan.name.lowercase()))
}

sealed interface PolicyVerdict {
    data object Ok : PolicyVerdict
    data class Rejected(val reason: String) : PolicyVerdict
}

/**
 * The privacy gate every event passes through, in debug and in release. It checks the SHAPE of an event, so even a future
 * mistake (someone adds a name or an id as a parameter) is stopped before it leaves the phone.
 */
object AnalyticsPolicy {
    private val token = Regex("^[a-z0-9_]{1,${AnalyticsSpec.MAX_VALUE_LENGTH}}$")
    private val longDigits = Regex("[0-9]{6,}")

    fun check(name: String, params: Map<String, String>): PolicyVerdict {
        val allowed = AnalyticsSpec.EVENTS[name] ?: return PolicyVerdict.Rejected("unknown event $name")
        if (params.size > AnalyticsSpec.MAX_PARAMS) return PolicyVerdict.Rejected("too many parameters")
        for ((key, value) in params) {
            if (key !in allowed) return PolicyVerdict.Rejected("parameter $key is not allowed on $name")
            if (!token.matches(value)) return PolicyVerdict.Rejected("value of $key is not a short lowercase token")
            // A long run of digits looks like a phone number, an id or a timestamp.
            if (longDigits.containsMatchIn(value)) return PolicyVerdict.Rejected("value of $key looks like a number or id")
        }
        return PolicyVerdict.Ok
    }

    fun check(event: AnalyticsEvent): PolicyVerdict = check(event.name, event.params)
}

/** Where events finally go (Firebase Analytics in the app, a list in tests). */
interface AnalyticsSink {
    fun log(name: String, params: Map<String, String>)
    fun setUserProperty(name: String, value: String)
}

enum class UsageDataChoice { UNDECIDED, ALLOWED, DECLINED }

/**
 * The only door to analytics. Nothing is sent until the person chose "allow"; a declined or undecided choice sends
 * nothing at all. Every event is checked by [AnalyticsPolicy] first.
 */
class Analytics(
    private val sink: AnalyticsSink,
    private val choice: () -> UsageDataChoice,
    private val onRejected: (String) -> Unit = {},
) {
    fun track(event: AnalyticsEvent) {
        if (choice() != UsageDataChoice.ALLOWED) return
        when (val verdict = AnalyticsPolicy.check(event)) {
            PolicyVerdict.Ok -> sink.log(event.name, event.params)
            is PolicyVerdict.Rejected -> onRejected(verdict.reason)
        }
    }

    fun setPlan(isPro: Boolean) {
        if (choice() != UsageDataChoice.ALLOWED) return
        sink.setUserProperty(AnalyticsSpec.USER_PROPERTY_PLAN, if (isPro) "pro" else "free")
    }
}

/** Finds the moment a permission turns on, so `permission_granted` is sent once per change and not on every launch. */
object PermissionTransitions {
    /** [previous] null means the first time we looked: that is only a baseline, nothing is reported. */
    fun granted(previous: Map<AnalyticsPermission, Boolean>?, current: Map<AnalyticsPermission, Boolean>): List<AnalyticsPermission> {
        if (previous == null) return emptyList()
        return current.filter { (p, on) -> on && previous[p] == false }.keys.toList()
    }
}

/** Where the person's answer to the "anonymous data" question is kept. */
interface UsageDataChoiceStore {
    val choice: kotlinx.coroutines.flow.StateFlow<UsageDataChoice>
    fun set(choice: UsageDataChoice)
}
