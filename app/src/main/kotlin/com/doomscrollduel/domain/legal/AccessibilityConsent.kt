package com.doomscrollduel.domain.legal

/**
 * The person's answer to the Accessibility disclosure. Google Play requires the disclosure to be shown BEFORE the
 * permission is requested and to be accepted with an affirmative tap ("Agree"). Nothing here is implied: no record
 * means no consent, and closing the screen, pressing Back or "Not now" records nothing.
 */
data class ConsentRecord(val version: Int, val acceptedAtMs: Long)

object AccessibilityConsent {
    /**
     * Version of the disclosure text. Bump it whenever the text changes in a way that matters (new app, new thing the
     * service does, new data use). Everyone is then asked again. `scripts/check-play-readiness.sh` fails when the text
     * changes without this number and the pinned hash being updated.
     */
    const val DISCLOSURE_VERSION = 1

    /** True only when the person agreed to THIS version of the text. */
    fun isValid(record: ConsentRecord?): Boolean = record != null && record.version == DISCLOSURE_VERSION

    /** The system Accessibility settings may be opened only after a valid consent. */
    fun mayRequestPermission(record: ConsentRecord?): Boolean = isValid(record)

    /** The reel counter and blocker do nothing at all without a valid consent, even if the service is switched on. */
    fun mayProcessEvents(record: ConsentRecord?): Boolean = isValid(record)

    fun accept(nowMs: Long): ConsentRecord = ConsentRecord(DISCLOSURE_VERSION, nowMs)
}

/** Where the answer is kept. Local to the phone; reinstalling the app asks again. */
interface ConsentStore {
    val flow: kotlinx.coroutines.flow.StateFlow<ConsentRecord?>
    fun current(): ConsentRecord?
    fun save(record: ConsentRecord)
}
