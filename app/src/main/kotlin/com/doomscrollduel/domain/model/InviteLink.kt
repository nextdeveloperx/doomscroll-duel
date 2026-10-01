package com.doomscrollduel.domain.model

/**
 * Invite code: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L). The server creates
 * the codes; the app only checks the shape before asking the server to accept one.
 */
@JvmInline
value class InviteCode private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        const val LENGTH = 8
        const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        fun parse(input: String): InviteCode? {
            val code = input.trim().uppercase()
            return if (code.length == LENGTH && code.all { it in ALPHABET }) InviteCode(code) else null
        }
    }
}

/** Builds and reads invite links: `https://<host>/invite/<CODE>` or `doomscrollduel://invite/<CODE>`. */
class InviteLinks(private val webHost: String) {
    fun build(code: InviteCode): String = "https://$webHost/invite/$code"

    /** Returns the code from [link], or null when it is not one of our invite links. */
    fun parse(link: String): InviteCode? {
        val text = link.trim()
        val path = when {
            text.startsWith("https://$webHost/invite/", ignoreCase = true) -> text.substring("https://$webHost/invite/".length)
            text.startsWith("$SCHEME://invite/", ignoreCase = true) -> text.substring("$SCHEME://invite/".length)
            else -> return null
        }
        return InviteCode.parse(path.substringBefore('?').substringBefore('#').trimEnd('/'))
    }

    companion object {
        const val SCHEME = "doomscrollduel"
    }
}
