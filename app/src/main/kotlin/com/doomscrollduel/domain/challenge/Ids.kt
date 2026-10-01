package com.doomscrollduel.domain.challenge

@JvmInline
value class PlayerId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlayerId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ChallengeId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChallengeId must not be blank" }
    }

    override fun toString(): String = value
}
