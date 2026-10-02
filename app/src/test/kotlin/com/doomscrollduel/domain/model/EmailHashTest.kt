package com.doomscrollduel.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHashTest {
    @Test
    fun `matches the value the server computes for the same address`() {
        // sha256("test@example.com"), checked against node's crypto in functions/src/socialRules.ts (emailHash).
        assertEquals("973dfe463ec85785f5f95af5ba3906eedb2d931c24e69824a89ea65dba4e813b", EmailHash.of("test@example.com"))
    }

    @Test
    fun `case and surrounding spaces do not change the hash`() {
        assertEquals(EmailHash.of("test@example.com"), EmailHash.of("  Test@Example.COM "))
    }

    @Test
    fun `different addresses give different hashes of 64 hex characters`() {
        val a = EmailHash.of("a@b.co")
        val b = EmailHash.of("a@b.cc")
        assertNotEquals(a, b)
        assertTrue(a.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `ofAll drops junk, repeats and respects the limit`() {
        val emails = listOf("x@y.com", "X@Y.COM", "", "no-at-sign", "@x.com", "x@", "z@y.com")
        assertEquals(listOf(EmailHash.of("x@y.com"), EmailHash.of("z@y.com")), EmailHash.ofAll(emails))
        val many = (1..50).map { "user$it@mail.com" }
        assertEquals(10, EmailHash.ofAll(many, limit = 10).size)
    }
}
