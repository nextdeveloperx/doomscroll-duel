package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class PaneAppearedCountingTest {
    private val yt = SurfaceRule(TrackedApp.YOUTUBE, setOf("reel_recycler"), countOnPaneAppeared = true)
    private val processor = ReelEventProcessor(rules = { SurfaceRules(listOf(yt)) })

    private fun pane(at: Long, open: Boolean = true) = ReelSignal.ContentChanged(TrackedApp.YOUTUBE, at, open)

    @Test fun `every swipe with a pane change counts once even without any scroll event`() {
        val counted = listOf(0L, 1_500L, 3_000L).count { processor.process(pane(it)) }
        assertEquals(3, counted)
    }

    @Test fun `the twin pane event a few ms later is not a second reel`() {
        assertTrue(processor.process(pane(1_000)))
        assertFalse(processor.process(pane(1_002)))
    }

    @Test fun `a pane change while the reel screen is not open counts nothing`() {
        assertFalse(processor.process(pane(1_000, open = false)))
    }

    @Test fun `a swipe already counted by its scroll is not counted again by its pane change`() {
        processor.process(ReelSignal.Scroll(TrackedApp.YOUTUBE, 1_000, null, "com.google.android.youtube:id/reel_recycler"))
        assertTrue(processor.process(ReelSignal.ContentChanged(TrackedApp.YOUTUBE, 1_020)))
        assertFalse(processor.process(pane(1_300)))
    }

    @Test fun `an app without the flag ignores pane changes`() {
        val p = ReelEventProcessor(rules = { SurfaceRules(listOf(yt.copy(countOnPaneAppeared = false))) })
        assertFalse(p.process(pane(1_000)))
    }
}
