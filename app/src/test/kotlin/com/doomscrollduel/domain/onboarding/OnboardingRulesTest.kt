package com.doomscrollduel.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRulesTest {
    @Test fun `under the minimum age is refused, the minimum itself is fine`() {
        assertFalse(AgeRule.allowed(AgeRule.MIN_AGE - 1))
        assertTrue(AgeRule.allowed(AgeRule.MIN_AGE))
        assertTrue(AgeRule.allowed(AgeRule.DEFAULT_AGE))
    }

    @Test fun `the default age is allowed and inside the stepper range`() {
        assertTrue(AgeRule.DEFAULT_AGE in AgeRule.LOWEST_SHOWN..AgeRule.MAX_AGE)
        assertEquals(AgeRule.DEFAULT_AGE, AgeRule.clamp(AgeRule.DEFAULT_AGE))
    }

    @Test fun `clamp keeps the stepper in range`() {
        assertEquals(AgeRule.LOWEST_SHOWN, AgeRule.clamp(0))
        assertEquals(AgeRule.MAX_AGE, AgeRule.clamp(500))
    }

    @Test fun `only real letters buzz`() {
        assertTrue(Typing.shouldBuzz('a'))
        assertTrue(Typing.shouldBuzz('?'))
        assertFalse(Typing.shouldBuzz(' '))
        assertFalse(Typing.shouldBuzz('\n'))
    }

    @Test fun `visible text grows letter by letter and never overflows`() {
        assertEquals("", Typing.visible("hello", 0))
        assertEquals("hel", Typing.visible("hello", 3))
        assertEquals("hello", Typing.visible("hello", 99))
        assertEquals("", Typing.visible("hello", -4))
    }
}
