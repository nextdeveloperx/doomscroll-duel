package com.doomscrollduel.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileExtrasTest {

    @Test fun `a plain handle, an at-handle and a pasted link all give the same handle`() {
        assertEquals("vikash_07", SocialHandles.clean("vikash_07"))
        assertEquals("vikash_07", SocialHandles.clean("  @vikash_07 "))
        assertEquals("vikash_07", SocialHandles.clean("https://www.instagram.com/vikash_07/?hl=en"))
        assertEquals("vikash_07", SocialHandles.clean("instagram.com/vikash_07"))
        assertEquals("vikash", SocialHandles.clean("https://www.youtube.com/@vikash"))
    }

    @Test fun `anything that is not a plain handle is dropped`() {
        assertEquals("", SocialHandles.clean(""))
        assertEquals("", SocialHandles.clean("   "))
        assertEquals("", SocialHandles.clean("javascript:alert(1)"))
        assertEquals("", SocialHandles.clean("two words"))
        assertEquals("", SocialHandles.clean("a".repeat(SocialHandles.MAX_HANDLE + 1)))
        assertEquals("", SocialHandles.clean("https://evil.example/?next=http://x"))
    }

    @Test fun `the link is built from the handle, never taken from the input`() {
        assertEquals("https://www.instagram.com/abc", SocialApp.INSTAGRAM.url("abc"))
        assertEquals("https://www.youtube.com/@abc", SocialApp.YOUTUBE.url("abc"))
        assertEquals("https://www.snapchat.com/add/abc", SocialApp.SNAPCHAT.url("abc"))
        assertEquals("https://www.facebook.com/abc", SocialApp.FACEBOOK.url("abc"))
        assertNull(SocialApp.INSTAGRAM.url(""))
    }

    @Test fun `cleaning trims the bio and reduces every handle`() {
        val cleaned = ProfileExtras(bio = "  hello ".padEnd(400, 'x'), instagram = "@me", youtube = "https://youtube.com/@me", snapchat = "no no", facebook = "").cleaned()
        assertEquals(ProfileExtras.MAX_BIO, cleaned.bio.length)
        assertEquals("me", cleaned.instagram)
        assertEquals("me", cleaned.youtube)
        assertEquals("", cleaned.snapchat)
        assertEquals("", cleaned.facebook)
    }

    @Test fun `an empty profile is empty and one with a link is not`() {
        assertTrue(ProfileExtras().isEmpty)
        assertFalse(ProfileExtras(facebook = "me").isEmpty)
        assertFalse(ProfileExtras(bio = "hi").isEmpty)
    }

    @Test fun `with and handle round trip for every app`() {
        SocialApp.entries.forEach { app -> assertEquals("h", ProfileExtras().with(app, "h").handle(app)) }
    }

    @Test fun `normal style leaves the name alone`() {
        assertEquals("Vikash 07", NameStyle.NORMAL.apply("Vikash 07"))
    }

    @Test fun `styles change letters only, digits spaces and symbols stay`() {
        NameStyle.entries.filter { it != NameStyle.NORMAL }.forEach { style ->
            val out = style.apply("Aa 1_")
            assertTrue("${style.name} keeps the digit", "1" in out)
            assertTrue("${style.name} keeps the space", " " in out)
            assertTrue("${style.name} keeps the underscore", "_" in out)
            assertFalse("${style.name} really changes the letters", "A" in out || "a" in out)
        }
    }

    @Test fun `known letters come out right`() {
        assertEquals("𝗔𝗮", NameStyle.BOLD.apply("Aa"))
        assertEquals("𝓐𝓪", NameStyle.SCRIPT.apply("Aa"))
        assertEquals("𝔸𝕒", NameStyle.DOUBLE.apply("Aa"))
        assertEquals("Ⓐⓐ", NameStyle.CIRCLED.apply("Aa"))
        assertEquals("ＡＡ".replace("Ａ", "Ａ").take(1) + "ａ", NameStyle.WIDE.apply("Aa"))
        assertEquals("ᴀᴀ", NameStyle.SMALL_CAPS.apply("Aa"))
    }

    @Test fun `the double-struck capitals with holes use their own letters`() {
        assertEquals("ℂℍℕℙℚℝℤ", NameStyle.DOUBLE.apply("CHNPQRZ"))
    }

    @Test fun `a styled name is cut so it never passes the limit`() {
        val long = "abcdefghijklmnopqrstuvwxyz"
        NameStyle.entries.forEach { style ->
            val fitted = NameStyle.fit(long, style)
            assertTrue("${style.name} fits", style.apply(fitted).length <= NameStyle.MAX_NAME)
            // and it is the longest prefix that fits
            if (fitted.length < long.length) assertTrue(style.apply(long.take(fitted.length + 1)).length > NameStyle.MAX_NAME)
        }
    }

    @Test fun `cutting a fancy name never leaves half a letter`() {
        val fitted = NameStyle.fit("a".repeat(40), NameStyle.BOLD)
        val styled = NameStyle.BOLD.apply(fitted)
        assertFalse(Character.isHighSurrogate(styled.last()))
        assertEquals(styled.codePointCount(0, styled.length) * 2, styled.length)
    }
}
