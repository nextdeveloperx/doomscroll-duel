package com.doomscrollduel.domain.model

/** A validated, lowercase username. Friends find each other by this; no phone contacts are uploaded. */
@JvmInline
value class Username private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 20
        private val reserved = setOf("admin", "support", "help", "doomscroll", "duel", "official", "moderator", "system")

        /** Trims, drops a leading "@" and lowercases, then checks the rules. */
        fun parse(input: String): UsernameResult {
            val name = input.trim().removePrefix("@").lowercase()
            return when {
                name.length < MIN_LENGTH -> UsernameResult.Invalid(UsernameProblem.TOO_SHORT)
                name.length > MAX_LENGTH -> UsernameResult.Invalid(UsernameProblem.TOO_LONG)
                name.any { it !in 'a'..'z' && it !in '0'..'9' && it != '_' && it != '.' } ->
                    UsernameResult.Invalid(UsernameProblem.BAD_CHARACTERS)
                name.first() !in 'a'..'z' -> UsernameResult.Invalid(UsernameProblem.MUST_START_WITH_LETTER)
                name.last() == '_' || name.last() == '.' || ".." in name || "__" in name || "._" in name || "_." in name ->
                    UsernameResult.Invalid(UsernameProblem.BAD_SEPARATORS)
                name in reserved -> UsernameResult.Invalid(UsernameProblem.RESERVED)
                else -> UsernameResult.Valid(Username(name))
            }
        }
    }
}

enum class UsernameProblem { TOO_SHORT, TOO_LONG, BAD_CHARACTERS, MUST_START_WITH_LETTER, BAD_SEPARATORS, RESERVED }

sealed interface UsernameResult {
    data class Valid(val username: Username) : UsernameResult
    data class Invalid(val problem: UsernameProblem) : UsernameResult
}
