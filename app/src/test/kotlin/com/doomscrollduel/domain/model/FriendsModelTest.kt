package com.doomscrollduel.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsModelTest {
    private fun username(text: String) = Username.parse(text)

    @Test
    fun `valid usernames are normalised`() {
        assertEquals("aman_31", (username("  @Aman_31 ") as UsernameResult.Valid).username.value)
        assertTrue(username("riya.sharma") is UsernameResult.Valid)
    }

    @Test
    fun `username problems are reported`() {
        fun problem(text: String) = (username(text) as UsernameResult.Invalid).problem
        assertEquals(UsernameProblem.TOO_SHORT, problem("ab"))
        assertEquals(UsernameProblem.TOO_LONG, problem("a".repeat(21)))
        assertEquals(UsernameProblem.BAD_CHARACTERS, problem("aman kumar"))
        assertEquals(UsernameProblem.BAD_CHARACTERS, problem("aman-k"))
        assertEquals(UsernameProblem.BAD_CHARACTERS, problem("अमन123"))
        assertEquals(UsernameProblem.MUST_START_WITH_LETTER, problem("1aman"))
        assertEquals(UsernameProblem.MUST_START_WITH_LETTER, problem("_aman"))
        assertEquals(UsernameProblem.BAD_SEPARATORS, problem("aman_"))
        assertEquals(UsernameProblem.BAD_SEPARATORS, problem("aman..k"))
        assertEquals(UsernameProblem.BAD_SEPARATORS, problem("aman._k"))
        assertEquals(UsernameProblem.RESERVED, problem("Admin"))
    }

    @Test
    fun `invite codes check shape and reject look-alike characters`() {
        assertEquals("AB23CD45", InviteCode.parse(" ab23cd45 ")?.value)
        assertNull(InviteCode.parse("AB23CD4")) // too short
        assertNull(InviteCode.parse("AB23CD456")) // too long
        assertNull(InviteCode.parse("AB23CD4O")) // letter O
        assertNull(InviteCode.parse("AB23CD41")) // digit 1
        assertNull(InviteCode.parse("AB23CD4I")) // letter I
    }

    @Test
    fun `invite links round trip and reject foreign links`() {
        val links = InviteLinks("doomscrollduel.app")
        val code = InviteCode.parse("K7M2QX9A")!!
        val web = links.build(code)
        assertEquals("https://doomscrollduel.app/invite/K7M2QX9A", web)
        assertEquals(code, links.parse(web))
        assertEquals(code, links.parse("doomscrollduel://invite/k7m2qx9a"))
        assertEquals(code, links.parse("https://doomscrollduel.app/invite/K7M2QX9A/?utm=x#frag"))
        assertNull(links.parse("https://evil.example/invite/K7M2QX9A"))
        assertNull(links.parse("https://doomscrollduel.app/profile/K7M2QX9A"))
        assertNull(links.parse("https://doomscrollduel.app/invite/short"))
        assertNotNull(links.parse("  doomscrollduel://invite/K7M2QX9A  "))
    }

    @Test
    fun `indian phone numbers become e164`() {
        assertEquals("+919876543210", PhoneNumber.parseIndian("98765 43210")?.e164)
        assertEquals("+919876543210", PhoneNumber.parseIndian("+91 98765-43210")?.e164)
        assertEquals("+919876543210", PhoneNumber.parseIndian("09876543210")?.e164)
        assertEquals("+919876543210", PhoneNumber.parseIndian("919876543210")?.e164)
        assertNull(PhoneNumber.parseIndian("5876543210")) // mobile numbers start 6 to 9
        assertNull(PhoneNumber.parseIndian("987654321")) // 9 digits
        assertNull(PhoneNumber.parseIndian("98765432101")) // 11 digits without leading 0
        assertNull(PhoneNumber.parseIndian(""))
    }
}
