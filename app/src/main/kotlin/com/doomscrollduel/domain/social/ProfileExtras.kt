package com.doomscrollduel.domain.social

/** The social apps a person can link on their profile. Only the handle is stored; the link is built here, so nobody can put any other address in. */
enum class SocialApp(val key: String, private val base: String, private val prefix: String = "") {
    INSTAGRAM("instagram", "https://www.instagram.com/"),
    YOUTUBE("youtube", "https://www.youtube.com/", "@"),
    SNAPCHAT("snapchat", "https://www.snapchat.com/add/"),
    FACEBOOK("facebook", "https://www.facebook.com/"),
    ;

    /** The address that opens this handle, or null when [handle] is empty. */
    fun url(handle: String): String? = handle.takeIf { it.isNotEmpty() }?.let { base + prefix + it }
}

/** What a person adds to their profile besides the name: a short bio and up to four social handles. */
data class ProfileExtras(
    val bio: String = "",
    val instagram: String = "",
    val youtube: String = "",
    val snapchat: String = "",
    val facebook: String = "",
) {
    fun handle(app: SocialApp): String = when (app) {
        SocialApp.INSTAGRAM -> instagram
        SocialApp.YOUTUBE -> youtube
        SocialApp.SNAPCHAT -> snapchat
        SocialApp.FACEBOOK -> facebook
    }

    fun with(app: SocialApp, handle: String): ProfileExtras = when (app) {
        SocialApp.INSTAGRAM -> copy(instagram = handle)
        SocialApp.YOUTUBE -> copy(youtube = handle)
        SocialApp.SNAPCHAT -> copy(snapchat = handle)
        SocialApp.FACEBOOK -> copy(facebook = handle)
    }

    val isEmpty: Boolean get() = bio.isBlank() && SocialApp.entries.all { handle(it).isEmpty() }

    /** The same data cleaned for saving: bio trimmed to [MAX_BIO], every handle reduced to a plain handle. */
    fun cleaned(): ProfileExtras = ProfileExtras(
        bio = bio.trim().take(MAX_BIO),
        instagram = SocialHandles.clean(instagram),
        youtube = SocialHandles.clean(youtube),
        snapchat = SocialHandles.clean(snapchat),
        facebook = SocialHandles.clean(facebook),
    )

    companion object {
        const val MAX_BIO = 150
    }
}

object SocialHandles {
    const val MAX_HANDLE = 40
    private val allowed = Regex("[A-Za-z0-9._-]+")

    /**
     * Turns what the person typed into a plain handle: "@name", "name", or a pasted link such as
     * "https://instagram.com/name/?hl=en" all give "name". Anything that is not letters, digits, dot, underscore or dash gives "".
     */
    fun clean(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return ""
        // A pasted link: keep the last meaningful part of its path (before a query or fragment).
        // The first part is the site (instagram.com); a link with nothing after the site has no handle in it.
        if ("/" in text) {
            val parts = text.substringBefore('?').substringBefore('#').substringAfter("://").split('/').filter { it.isNotEmpty() }
            text = if (parts.size >= 2) parts.last() else ""
        }
        text = text.removePrefix("@").trim()
        if (text.isEmpty() || text.length > MAX_HANDLE || !allowed.matches(text)) return ""
        return text
    }
}

/**
 * Fancy letter styles for the display name ("Vikash" -> "𝓥𝓲𝓴𝓪𝓼𝓱"). The result is ordinary text, so every screen shows it as it is.
 * Only A-Z and a-z change; digits, spaces and other characters stay.
 */
enum class NameStyle(private val upperStart: Int, private val lowerStart: Int) {
    NORMAL(0, 0),
    BOLD(0x1D5D4, 0x1D5EE),
    SCRIPT(0x1D4D0, 0x1D4EA),
    GOTHIC(0x1D56C, 0x1D586),
    DOUBLE(0x1D538, 0x1D552),
    CIRCLED(0x24B6, 0x24D0),
    WIDE(0xFF21, 0xFF41),
    SMALL_CAPS(0, 0),
    ;

    fun apply(text: String): String {
        if (this == NORMAL) return text
        val out = StringBuilder()
        for (ch in text) {
            when {
                this == SMALL_CAPS && ch in 'A'..'Z' -> out.append(SMALL_CAPS_LETTERS[ch - 'A'])
                this == SMALL_CAPS && ch in 'a'..'z' -> out.append(SMALL_CAPS_LETTERS[ch - 'a'])
                this == SMALL_CAPS -> out.append(ch)
                // The double-struck block has seven holes (C H N P Q R Z); those capitals live elsewhere.
                this == DOUBLE && ch in DOUBLE_HOLES -> out.append(DOUBLE_HOLES.getValue(ch))
                ch in 'A'..'Z' -> out.appendCodePoint(upperStart + (ch - 'A'))
                ch in 'a'..'z' -> out.appendCodePoint(lowerStart + (ch - 'a'))
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** What the style looks like, for its chip. */
    val sample: String get() = apply("Aa")

    companion object {
        /** The longest display name (in characters as the phone counts them; a fancy letter can take two). */
        const val MAX_NAME = 30

        private val DOUBLE_HOLES = mapOf('C' to 'ℂ', 'H' to 'ℍ', 'N' to 'ℕ', 'P' to 'ℙ', 'Q' to 'ℚ', 'R' to 'ℝ', 'Z' to 'ℤ')

        private const val SMALL_CAPS_LETTERS ="ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"

        /** [text] in [style], cut so that the styled result is no longer than [MAX_NAME]. */
        fun fit(text: String, style: NameStyle): String {
            var plain = text
            while (plain.isNotEmpty() && style.apply(plain).length > MAX_NAME) plain = plain.dropLast(1)
            return plain
        }
    }
}
