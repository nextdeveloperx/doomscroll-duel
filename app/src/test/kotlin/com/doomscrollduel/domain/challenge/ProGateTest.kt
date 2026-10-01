package com.doomscrollduel.domain.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGateTest {
    @Test
    fun `squad and strict lock are Pro, the rest are free`() {
        assertEquals(setOf(ChallengeMode.SQUAD, ChallengeMode.STRICT_LOCK), ChallengeMode.entries.filter { it.isPro }.toSet())
    }

    @Test
    fun `free users can create free modes and cannot create Pro modes`() {
        for (mode in ChallengeMode.entries) {
            assertEquals(!mode.isPro, ProGate.canCreate(mode, isPro = false))
            assertTrue(ProGate.canCreate(mode, isPro = true))
        }
    }

    @Test
    fun `anyone can join`() {
        ChallengeMode.entries.forEach { assertTrue(ProGate.canJoin(it)) }
    }

    @Test
    fun `only the duel uses coins`() {
        assertEquals(listOf(ChallengeMode.DUEL), ChallengeMode.entries.filter { it.usesCoins })
        assertFalse(ChallengeMode.FORFEIT_DARE.usesCoins)
    }
}
