package com.doomscrollduel.tracking

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.data.local.AppDatabase
import com.doomscrollduel.data.repository.RoomReelRepository
import com.doomscrollduel.tracking.detector.ReelEventProcessor
import com.doomscrollduel.tracking.detector.SurfaceRulesParser
import com.doomscrollduel.tracking.model.TrackedApp
import com.doomscrollduel.tracking.service.ReelEventPipeline
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feeds REAL `AccessibilityEvent` objects (package, class, type, time) through the same pipeline the accessibility service
 * uses, into a real Room database and the real surface rules from `assets/surface_rules.json`, and checks the counter.
 *
 * What is real: AccessibilityEvent, the pipeline, the processor, the rules file, Room, the repository.
 * What is simulated: the system delivering the events to a switched-on service, and the scrolled view's layout id (an
 * event built in a test has no source view, so the id is supplied the way the service would read it). The full system path
 * is covered by the manual matrix in `docs/device-test-matrix.md`.
 */
@RunWith(AndroidJUnit4::class)
class ReelCounterInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val wallNow = 1_790_000_000_000L // a fixed "now", so the day never rolls over during the test
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomReelRepository
    private lateinit var scope: CoroutineScope
    private var consent = true
    private val viewIds = HashMap<Long, String?>()
    private val counted = mutableListOf<Pair<TrackedApp, Int>>()

    private lateinit var pipeline: ReelEventPipeline

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomReelRepository(db.reelCountDao(), DayClock({ wallNow }, { zone }), { zone }, { wallNow })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val rulesText = context.assets.open("surface_rules.json").bufferedReader().use { it.readText() }
        val rules = SurfaceRulesParser.parse(rulesText)
        runBlocking { repository.ensureToday() }
        pipeline = ReelEventPipeline(
            processor = ReelEventProcessor(rules = { rules }),
            consent = { consent },
            viewIdOf = { viewIds[it.eventTime] },
            record = { app, at ->
                repository.recordReel(app, at)
                repository.todayTotal()
            },
            onCounted = { app, total -> synchronized(counted) { counted += app to total } },
            scope = scope,
            wallClock = { wallNow },
        )
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    // ----- helpers ---------------------------------------------------------------------------------

    private fun scroll(pkg: String, atMs: Long, viewId: String?, className: String = "androidx.viewpager2.widget.ViewPager2") {
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        e.packageName = pkg
        e.className = className
        e.eventTime = atMs
        viewIds[atMs] = viewId
        pipeline.onEvent(e)
    }

    private fun contentChanged(pkg: String, atMs: Long) {
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        e.packageName = pkg
        e.eventTime = atMs
        pipeline.onEvent(e)
    }

    /** One swipe: a scroll, then the new video appearing 200 ms later. */
    private fun swipe(pkg: String, atMs: Long, viewId: String) {
        scroll(pkg, atMs, viewId)
        contentChanged(pkg, atMs + 200)
    }

    private fun awaitTotal(expected: Int) = runBlocking {
        try {
            withTimeout(3_000) { while (repository.todayTotal() != expected) delay(25) }
        } catch (_: Exception) {
            // fall through to the assertion, which prints the real number
        }
        // Give a count that should NOT happen a moment to (wrongly) arrive before we assert.
        delay(150)
        assertEquals(expected, repository.todayTotal())
    }

    private val instagram = "com.instagram.android"
    private val instagramReels = "com.instagram.android:id/clips_viewer_view_pager"
    private val youtube = "com.google.android.youtube"
    private val youtubeShorts = "com.google.android.youtube:id/reel_recycler"

    // ----- the tests -------------------------------------------------------------------------------

    @Test fun fiveSwipesOnInstagramReelsCountFive() {
        for (i in 0 until 5) swipe(instagram, 10_000L + i * 2_000L, instagramReels)
        awaitTotal(5)
        val today = runBlocking { repository.observeToday().first() }
        assertEquals(5, today.perApp[TrackedApp.INSTAGRAM])
        assertEquals(0, today.perApp[TrackedApp.YOUTUBE])
        synchronized(counted) { assertEquals(listOf(1, 2, 3, 4, 5), counted.map { it.second }) }
    }

    @Test fun onlyReelsCountNotTheNormalFeedNorOtherApps() {
        swipe(instagram, 10_000, "com.instagram.android:id/feed_recycler_view") // the normal feed
        swipe("com.whatsapp", 12_000, "com.whatsapp:id/conversation_list")      // an app we never track
        contentChanged(instagram, 14_000)                                         // a like or a timer, no scroll before it
        swipe(instagram, 16_000, instagramReels)                                  // the one real reel
        awaitTotal(1)
    }

    @Test fun twoAppsAreCountedSeparately() {
        swipe(instagram, 10_000, instagramReels)
        swipe(youtube, 12_000, youtubeShorts)
        swipe(youtube, 14_000, youtubeShorts)
        awaitTotal(3)
        val today = runBlocking { repository.observeToday().first() }
        assertEquals(1, today.perApp[TrackedApp.INSTAGRAM])
        assertEquals(2, today.perApp[TrackedApp.YOUTUBE])
    }

    @Test fun oneSwipeIsOneReelEvenWithManyEvents() {
        // A burst of scroll events and several content changes from a single swipe.
        scroll(instagram, 10_000, instagramReels)
        scroll(instagram, 10_050, instagramReels)
        scroll(instagram, 10_100, instagramReels)
        contentChanged(instagram, 10_200)
        contentChanged(instagram, 10_260)
        contentChanged(instagram, 10_320)
        awaitTotal(1)
    }

    @Test fun twoSwipesCloserThanTheMinimumGapCountOnce() {
        swipe(instagram, 10_000, instagramReels)
        swipe(instagram, 10_300, instagramReels) // 300 ms later: faster than a person can watch a reel
        awaitTotal(1)
    }

    @Test fun nothingIsCountedWithoutConsent_andCountingStartsOnceGiven() {
        consent = false
        for (i in 0 until 3) swipe(instagram, 10_000L + i * 2_000L, instagramReels)
        awaitTotal(0)
        consent = true
        for (i in 0 until 2) swipe(instagram, 30_000L + i * 2_000L, instagramReels)
        awaitTotal(2)
    }

    @Test fun eventsFromOtherAppsAreIgnoredByThePipeline() {
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        e.packageName = "com.some.other.app"
        assertNull(pipeline.onEvent(e))
    }
}
