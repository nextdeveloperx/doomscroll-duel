package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Says which scrolling view in an app is its short-video pager (YouTube Shorts, Instagram Reels,
 * Facebook Reels, Snapchat Spotlight), so ordinary feed scrolling is not counted.
 *
 * A scroll matches when its view id (the part after `:id/`) is in [viewIds] and, if [classNames] is
 * not empty, its class name is in [classNames]. A scroll also matches when the app's front screen is one of its
 * [SurfaceRule.windowClassNames]. A rule with neither never matches: that app is simply not counted until they are known. [verified] records whether the ids were confirmed on a
 * real device (see docs/manual-test-checklist.md).
 */
data class SurfaceRule(
    val app: TrackedApp,
    val viewIds: Set<String>,
    val classNames: Set<String> = emptySet(),
    val verified: Boolean = false,
    /**
     * For an app whose own view ids are hidden (Facebook strips them): the class names of its full-screen short-video screens.
     * Any scroll while one of these is the app's front screen counts as a reel swipe. Empty for apps matched by view id.
     */
    val windowClassNames: Set<String> = emptySet(),
    /**
     * The app's short-video pager cannot be told from its feed by any name (Facebook hides its view ids), so its swipes are
     * recognised by distance instead: a pager moves one whole page per swipe. See ReelEventProcessor.
     */
    val pagerByDistance: Boolean = false,
)

class SurfaceRules(rules: List<SurfaceRule>) {
    private val byApp: Map<TrackedApp, SurfaceRule> = rules.associateBy { it.app }

    fun isConfigured(app: TrackedApp): Boolean =
        byApp[app]?.let { it.viewIds.isNotEmpty() || it.windowClassNames.isNotEmpty() || it.pagerByDistance } == true

    fun isPagerByDistance(app: TrackedApp): Boolean = byApp[app]?.pagerByDistance == true

    /** The pager view ids of [app], used to tell whether its reel screen is open. Empty when unknown. */
    fun viewIdsOf(app: TrackedApp): Set<String> = byApp[app]?.viewIds.orEmpty()

    fun matchesScroll(app: TrackedApp, className: String?, viewId: String?, windowClass: String? = null): Boolean {
        val rule = byApp[app] ?: return false
        if (windowClass != null && windowClass in rule.windowClassNames) return true
        if (rule.viewIds.isEmpty() || viewId == null) return false
        if (viewId.substringAfter(":id/") !in rule.viewIds) return false
        return rule.classNames.isEmpty() || (className != null && className in rule.classNames)
    }

    fun rules(): List<SurfaceRule> = byApp.values.toList()

    companion object {
        val Empty = SurfaceRules(emptyList())
    }
}

/** Holds the active rules so a newer set (remote config, later) can replace them while counting. */
class SurfaceRulesHolder(initial: SurfaceRules) {
    @Volatile
    var current: SurfaceRules = initial
        private set

    fun update(rules: SurfaceRules) {
        current = rules
    }
}

@Serializable
private data class RuleDto(
    @SerialName("package") val packageName: String,
    val viewIds: List<String> = emptyList(),
    val classNames: List<String> = emptyList(),
    val verified: Boolean = false,
    val windowClassNames: List<String> = emptyList(),
    val pagerByDistance: Boolean = false,
)

@Serializable
private data class RulesFileDto(val version: Int = 1, val rules: List<RuleDto> = emptyList())

object SurfaceRulesParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses `assets/surface_rules.json`. Unknown packages are skipped; malformed JSON throws. */
    fun parse(text: String): SurfaceRules {
        val file = json.decodeFromString<RulesFileDto>(text)
        val rules = file.rules.mapNotNull { dto ->
            TrackedApp.fromPackage(dto.packageName)?.let { app ->
                SurfaceRule(app, dto.viewIds.toSet(), dto.classNames.toSet(), dto.verified, dto.windowClassNames.toSet(), dto.pagerByDistance)
            }
        }
        return SurfaceRules(rules)
    }
}
