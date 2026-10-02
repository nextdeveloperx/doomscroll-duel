package com.doomscrollduel.domain.model

import java.security.MessageDigest

/**
 * How a contact's email is turned into what the phone is allowed to send: a SHA-256 hash of the trimmed, lowercase
 * address, as 64 hex characters. The server computes the same hash for each person who signed in, and matches hashes
 * only. `functions/src/socialRules.ts` (`emailHash`) must stay identical to this.
 */
object EmailHash {
    fun of(email: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Distinct hashes of the plausible addresses in [emails]. Anything without an @ is ignored. */
    fun ofAll(emails: Iterable<String>, limit: Int = MAX_HASHES): List<String> =
        emails.asSequence()
            .map { it.trim() }
            .filter { it.length in 5..254 && '@' in it && !it.startsWith("@") && !it.endsWith("@") }
            .map(::of)
            .distinct()
            .take(limit)
            .toList()

    /** The server accepts at most this many hashes in one request. */
    const val MAX_HASHES = 1000
}
