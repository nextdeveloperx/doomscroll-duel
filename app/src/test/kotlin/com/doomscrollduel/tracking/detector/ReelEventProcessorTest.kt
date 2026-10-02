package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelEventProcessorTest {
    private val rules = SurfaceRules(
        listOf(
            SurfaceRule(TrackedApp.INSTAGRAM, viewIds = setOf("clips_viewer_view_pager")),
            SurfaceRule(TrackedApp.YOUTUBE, viewIds = setOf("reel_recycler"), classNames = setOf("androidx.recyclerview.widget.RecyclerView")),
            SurfaceRule(TrackedApp.FACEBOOK, viewIds = emptySet()),
        ),
    )
    private val processor = ReelEventProcessor(rules = { rules })

    private fun igScroll(at: Long, id: String? = "com.instagram.android:id/clips_viewer_view_pager") =
        ReelSignal.Scroll(TrackedApp.INSTAGRAM, at, "androidx.viewpager2.widget.ViewPager2", id)

    private fun igChange(at: Long) = ReelSignal.ContentChanged(TrackedApp.INSTAGRAM, at)

    /** Feeds a swipe: a burst of scrolls, then the content change when the new reel appears. */
    private fun swipe(startAt: Long): Boolean {
        var counted = false
        for (t in listOf(0L, 16L, 32L, 48L, 64L, 80L)) counted = processor.process(igScroll(startAt + t)) || counted
        counted = processor.process(igChange(startAt + 120)) || counted
        return counted
    }

    @Test
    fun `one swipe counts exactly one reel`() {
        assertTrue(swipe(1_000))
    }

    @Test
    fun `extra events after the count do not count again`() {
        swipe(1_000)
        // Late scroll tail and several content changes from the same swipe.
        assertFalse(processor.process(igScroll(1_250)))
        assertFalse(processor.process(igChange(1_300)))
        assertFalse(processor.process(igChange(1_450)))
        assertFalse(processor.process(igChange(1_900)))
    }

    @Test
    fun `swipes 700 ms apart each count`() {
        var total = 0
        for (i in 0 until 5) if (swipe(1_000L + i * 700L)) total++
        assertEquals(5, total)
    }

    @Test
    fun `a swipe within 600 ms of the last counted reel is ignored`() {
        assertTrue(swipe(1_000)) // counted at 1120
        // Next swipe starts at 1400, only 280 ms after the count: ignored whole.
        assertFalse(swipe(1_400))
        // The one after that is far enough away.
        assertTrue(swipe(2_000))
    }

    @Test
    fun `content changes with no scroll never count`() {
        for (t in 0 until 20) assertFalse(processor.process(igChange(1_000L + t * 700L)))
    }

    @Test
    fun `scrolls on other surfaces never count`() {
        val feed = ReelSignal.Scroll(TrackedApp.INSTAGRAM, 1_000, "androidx.recyclerview.widget.RecyclerView", "com.instagram.android:id/feed_list")
        assertFalse(processor.process(feed))
        assertFalse(processor.process(igChange(1_100)))
        val noId = ReelSignal.Scroll(TrackedApp.INSTAGRAM, 3_000, "x", null)
        assertFalse(processor.process(noId))
        assertFalse(processor.process(igChange(3_100)))
    }

    @Test
    fun `content change too long after the scroll does not count`() {
        processor.process(igScroll(1_000))
        assertFalse(processor.process(igChange(1_000 + ReelEventProcessor.SWIPE_WINDOW_MILLIS + 1)))
        // And the stale swipe is gone: a later change alone does nothing.
        assertFalse(processor.process(igChange(5_000)))
    }

    @Test
    fun `a drag that never changes the page counts nothing`() {
        for (t in 0..5) processor.process(igScroll(1_000L + t * 16))
        // Nothing follows. Then a normal swipe a bit later still counts once.
        assertTrue(swipe(10_000))
    }

    @Test
    fun `class name must match when the rule names one`() {
        val good = ReelSignal.Scroll(TrackedApp.YOUTUBE, 1_000, "androidx.recyclerview.widget.RecyclerView", "com.google.android.youtube:id/reel_recycler")
        val badClass = ReelSignal.Scroll(TrackedApp.YOUTUBE, 5_000, "android.widget.ScrollView", "com.google.android.youtube:id/reel_recycler")
        processor.process(good)
        assertTrue(processor.process(ReelSignal.ContentChanged(TrackedApp.YOUTUBE, 1_100)))
        processor.process(badClass)
        assertFalse(processor.process(ReelSignal.ContentChanged(TrackedApp.YOUTUBE, 5_100)))
    }

    @Test
    fun `facebook counts only while its full-screen reel viewer is the front screen`() {
        val fb = SurfaceRules(
            listOf(SurfaceRule(TrackedApp.FACEBOOK, viewIds = emptySet(), windowClassNames = setOf("com.facebook.katana.immersiveactivity.ImmersiveActivity"))),
        )
        val p = ReelEventProcessor(rules = { fb })
        val recycler = "androidx.recyclerview.widget.RecyclerView"
        val hidden = "com.facebook.katana:id/(name removed)"
        // the normal feed (main tab screen): scrolling is not counted
        p.process(ReelSignal.Scroll(TrackedApp.FACEBOOK, 1_000, recycler, hidden, "com.facebook.katana.activity.FbMainTabActivity"))
        assertFalse(p.process(ReelSignal.ContentChanged(TrackedApp.FACEBOOK, 1_100)))
        // no screen known yet: not counted either
        p.process(ReelSignal.Scroll(TrackedApp.FACEBOOK, 3_000, recycler, hidden, null))
        assertFalse(p.process(ReelSignal.ContentChanged(TrackedApp.FACEBOOK, 3_100)))
        // the reel viewer: one swipe, one reel
        p.process(ReelSignal.Scroll(TrackedApp.FACEBOOK, 5_000, recycler, hidden, "com.facebook.katana.immersiveactivity.ImmersiveActivity"))
        assertTrue(p.process(ReelSignal.ContentChanged(TrackedApp.FACEBOOK, 5_100)))
    }

    @Test
    fun `an app with no known ids is never counted`() {
        processor.process(ReelSignal.Scroll(TrackedApp.FACEBOOK, 1_000, "c", "com.facebook.katana:id/anything"))
        assertFalse(processor.process(ReelSignal.ContentChanged(TrackedApp.FACEBOOK, 1_100)))
        processor.process(ReelSignal.Scroll(TrackedApp.SNAPCHAT, 1_000, "c", "id"))
        assertFalse(processor.process(ReelSignal.ContentChanged(TrackedApp.SNAPCHAT, 1_100)))
    }

    @Test
    fun `apps are tracked independently`() {
        processor.process(igScroll(1_000))
        processor.process(ReelSignal.Scroll(TrackedApp.YOUTUBE, 1_010, "androidx.recyclerview.widget.RecyclerView", "com.google.android.youtube:id/reel_recycler"))
        assertTrue(processor.process(igChange(1_100)))
        assertTrue(processor.process(ReelSignal.ContentChanged(TrackedApp.YOUTUBE, 1_110)))
    }

    @Test
    fun `reset forgets a pending swipe`() {
        processor.process(igScroll(1_000))
        processor.reset()
        assertFalse(processor.process(igChange(1_100)))
    }

    @Test
    fun `signals never print identifiers`() {
        val text = igScroll(5).toString()
        assertFalse("viewpager" in text.lowercase() || "clips" in text)
    }
}

class ReelDirectionAndDistanceTest {
    private val ig = SurfaceRule(TrackedApp.INSTAGRAM, viewIds = setOf("clips_viewer_view_pager"))
    private val fb = SurfaceRule(TrackedApp.FACEBOOK, viewIds = emptySet(), pagerByDistance = true)
    private val processor = ReelEventProcessor(rules = { SurfaceRules(listOf(ig, fb)) })
    private val igId = "com.instagram.android:id/clips_viewer_view_pager"
    private val hidden = "com.facebook.katana:id/(name removed)"
    private val recycler = "androidx.recyclerview.widget.RecyclerView"

    private fun igSwipe(startAt: Long, dy: Int): Boolean {
        var counted = false
        for (t in listOf(0L, 16L, 32L)) counted = processor.process(ReelSignal.Scroll(TrackedApp.INSTAGRAM, startAt + t, "x", igId, null, dy)) || counted
        return processor.process(ReelSignal.ContentChanged(TrackedApp.INSTAGRAM, startAt + 120)) || counted
    }

    /** One facebook swipe made of scroll events with these distances, 16 ms apart, then the pause that closes it. */
    private fun fbSwipe(startAt: Long, vararg parts: Int): Boolean {
        var counted = false
        parts.forEachIndexed { i, dy -> counted = processor.process(ReelSignal.Scroll(TrackedApp.FACEBOOK, startAt + i * 16L, recycler, hidden, null, dy)) || counted }
        return processor.flush(TrackedApp.FACEBOOK, startAt + parts.size * 16L + 340) || counted
    }

    @Test fun `going back to the previous reel does not count`() {
        assertTrue(igSwipe(1_000, +900))
        assertFalse(igSwipe(3_000, -900))
        assertTrue(igSwipe(5_000, +900))
    }

    @Test fun `a swipe with no direction from the phone still counts`() {
        assertTrue(igSwipe(1_000, 0))
    }

    @Test fun `facebook reels tab - the same page distance again and again counts, whatever the finger did`() {
        // the first swipe teaches the size (no hint), the next ones match it
        assertFalse(fbSwipe(1_000, 486, 466, 1112, 57))
        assertTrue(fbSwipe(3_000, 238, 234, 163, 1412, 73))
        assertTrue(fbSwipe(5_000, 949, 778, 393))
    }

    @Test fun `facebook reels tab - going back is not counted`() {
        fbSwipe(1_000, 486, 466, 1112, 57)
        assertFalse(fbSwipe(3_000, -325, -408, -1124, -263))
        assertTrue(fbSwipe(5_000, 949, 778, 393))
    }

    @Test fun `facebook feed - scrolls of different lengths are not counted`() {
        assertFalse(fbSwipe(1_000, 504, 484, 494, 219, 143, 72, 28, 2))
        assertFalse(fbSwipe(3_000, 220, 248, 276, 114, 40, 7))
        assertFalse(fbSwipe(5_000, 875, 1026, 580, 727, 325, 116))
        assertFalse(fbSwipe(7_000, 300, 200))
    }

    @Test fun `the page-height hint lets the very first swipe count`() {
        processor.pageHintPx = 2_120
        assertTrue(fbSwipe(1_000, 486, 466, 1112, 57))
    }

    @Test fun `sideways scrolls and tiny nudges say nothing`() {
        processor.pageHintPx = 2_120
        assertFalse(fbSwipe(1_000, 0, 0, 0))
        assertFalse(fbSwipe(3_000, 40, 30))
    }
}
