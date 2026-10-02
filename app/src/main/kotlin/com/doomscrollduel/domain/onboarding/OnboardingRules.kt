package com.doomscrollduel.domain.onboarding

/**
 * The age step. The age stays on the phone (it is never sent anywhere); it only decides whether the person may go on.
 * The minimum matches the Terms and Privacy Policy texts (16). Raising it to 18 is an open owner decision: change [MIN_AGE]
 * and the texts together.
 */
object AgeRule {
    const val MIN_AGE = 16
    const val MAX_AGE = 80
    const val DEFAULT_AGE = 18
    const val LOWEST_SHOWN = 10

    fun allowed(age: Int): Boolean = age >= MIN_AGE

    /** Keeps the stepper inside the range it can show. */
    fun clamp(age: Int): Int = age.coerceIn(LOWEST_SHOWN, MAX_AGE)
}

/** The typing effect on the onboarding pages. */
object Typing {
    /** Heading letters come slowly, body letters faster. */
    const val HEADING_MS = 46L
    const val BODY_MS = 22L

    /** Spaces and line breaks type silently; every real letter gives the phone a small tick. */
    fun shouldBuzz(c: Char): Boolean = !c.isWhitespace()

    /** How much of [text] is visible after [typed] letters have been typed (never more than the text, never negative). */
    fun visible(text: String, typed: Int): String = text.take(typed.coerceIn(0, text.length))
}
