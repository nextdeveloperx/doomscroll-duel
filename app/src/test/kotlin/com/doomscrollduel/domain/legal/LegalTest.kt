package com.doomscrollduel.domain.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityConsentTest {
    @Test fun `no record means no consent`() {
        assertFalse(AccessibilityConsent.isValid(null))
        assertFalse(AccessibilityConsent.mayRequestPermission(null))
        assertFalse(AccessibilityConsent.mayProcessEvents(null))
    }

    @Test fun `accepting the current text is valid`() {
        val r = AccessibilityConsent.accept(5L)
        assertEquals(AccessibilityConsent.DISCLOSURE_VERSION, r.version)
        assertTrue(AccessibilityConsent.isValid(r))
        assertTrue(AccessibilityConsent.mayProcessEvents(r))
    }

    @Test fun `an older or newer version is not consent to this text`() {
        assertFalse(AccessibilityConsent.isValid(ConsentRecord(AccessibilityConsent.DISCLOSURE_VERSION - 1, 1)))
        assertFalse(AccessibilityConsent.isValid(ConsentRecord(AccessibilityConsent.DISCLOSURE_VERSION + 1, 1)))
    }
}

class DeleteConfirmationTest {
    @Test fun `the word must match, case and spaces aside`() {
        assertTrue(DeleteConfirmation.wordMatches("DELETE"))
        assertTrue(DeleteConfirmation.wordMatches("  delete "))
        assertFalse(DeleteConfirmation.wordMatches("DELET"))
        assertFalse(DeleteConfirmation.wordMatches(""))
        assertFalse(DeleteConfirmation.wordMatches("DELETE ME"))
    }

    @Test fun `button needs the word, the tick and no job running`() {
        assertTrue(DeleteConfirmation.canDelete("delete", understood = true, busy = false))
        assertFalse(DeleteConfirmation.canDelete("delete", understood = false, busy = false))
        assertFalse(DeleteConfirmation.canDelete("nope", understood = true, busy = false))
        assertFalse(DeleteConfirmation.canDelete("delete", understood = true, busy = true))
    }

    @Test fun `scope lists are not empty and do not overlap`() {
        assertTrue(DeletionScope.removed.isNotEmpty() && DeletionScope.notRemoved.isNotEmpty())
        assertTrue(DeletionScope.removed.intersect(DeletionScope.notRemoved.toSet()).isEmpty())
    }
}
