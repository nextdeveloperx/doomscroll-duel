package com.doomscrollduel.domain.billing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The phone's copy of the server's entitlement, so Pro still works offline. Damaged text means "no entitlement". */
object EntitlementCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Dto(
        val status: String = "NONE",
        val plan: String? = null,
        val accessUntilMs: Long = 0L,
        val autoRenewing: Boolean = false,
        val verifiedAtMs: Long = 0L,
    )

    fun encode(e: Entitlement): String =
        json.encodeToString(Dto.serializer(), Dto(e.status.name, e.plan?.name, e.accessUntilMs, e.autoRenewing, e.verifiedAtMs))

    fun decode(text: String?): Entitlement {
        if (text.isNullOrBlank()) return Entitlement.None
        return runCatching { fromDto(json.decodeFromString(Dto.serializer(), text)) }.getOrDefault(Entitlement.None)
    }

    /** From a Firestore document's fields. Unknown or missing values grant nothing. */
    fun fromFields(fields: Map<String, Any?>): Entitlement = fromDto(
        Dto(
            status = fields["status"] as? String ?: "NONE",
            plan = fields["plan"] as? String,
            accessUntilMs = (fields["accessUntilMs"] as? Number)?.toLong() ?: 0L,
            autoRenewing = fields["autoRenewing"] as? Boolean ?: false,
            verifiedAtMs = (fields["verifiedAtMs"] as? Number)?.toLong() ?: 0L,
        ),
    )

    private fun fromDto(d: Dto) = Entitlement(
        status = EntitlementStatus.entries.firstOrNull { it.name == d.status } ?: EntitlementStatus.NONE,
        plan = ProPlan.entries.firstOrNull { it.name == d.plan },
        accessUntilMs = d.accessUntilMs,
        autoRenewing = d.autoRenewing,
        verifiedAtMs = d.verifiedAtMs,
    )
}
