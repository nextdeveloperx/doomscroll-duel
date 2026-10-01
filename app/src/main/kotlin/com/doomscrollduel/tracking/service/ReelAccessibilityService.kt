package com.doomscrollduel.tracking.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.doomscrollduel.blocking.BlockingPresenter
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.blocking.TrustedTime
import com.doomscrollduel.blocking.unlock.UnlockRepository
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.tracking.detector.SurfaceRulesHolder
import com.doomscrollduel.tracking.detector.ReelEventProcessor
import com.doomscrollduel.tracking.detector.ReelSignal
import com.doomscrollduel.tracking.model.TrackedApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Counts short videos swiped to in Instagram, YouTube, Facebook and Snapchat.
 *
 * PRIVACY CONTRACT (this is the whole reason the service is allowed to exist):
 *  - The system only delivers events from the four packages in `reel_accessibility_service.xml`.
 *  - From an event we read exactly: package name, event type, event time, and, for scroll events,
 *    the view's class name and layout resource id (e.g. "reel_recycler").
 *  - We never call getText, getContentDescription, getParent/getChild, or anything that walks the
 *    screen. Nothing the user sees or watches is read, stored or uploaded.
 *  - The blocker may ask the system "is a view with this layout id on screen?" (ReelScreenProbe, the only
 *    place allowed to) and may press Back or Home. It reads nothing from the answer.
 *  - All counting decisions live in [ReelEventProcessor], which only receives [ReelSignal].
 */
@AndroidEntryPoint
class ReelAccessibilityService : AccessibilityService() {

    @Inject lateinit var processor: ReelEventProcessor
    @Inject lateinit var repository: ReelRepository
    @Inject lateinit var blocking: BlockingController
    @Inject lateinit var scheduler: BlockingScheduler
    @Inject lateinit var unlock: UnlockRepository
    @Inject lateinit var trustedTime: TrustedTime
    @Inject lateinit var surfaceRules: SurfaceRulesHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var presenter: BlockingPresenter? = null
    private val debuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    override fun onServiceConnected() {
        super.onServiceConnected()
        processor.reset()
        presenter = BlockingPresenter(
            service = this,
            controller = blocking,
            probe = ReelScreenProbe { surfaceRules.current },
            unlock = unlock,
            trustedTime = trustedTime,
            scheduler = scheduler,
            scope = mainScope,
        )
        scheduler.schedule()
        scope.launch { repository.ensureToday() }
        // Best effort: Android may refuse to start a foreground service from the background.
        TrackingKeepAlive.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val app = TrackedApp.fromPackage(e.packageName?.toString()) ?: return
        val signal = when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> scrollSignal(app, e)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ReelSignal.ContentChanged(app, e.eventTime)
            // A window change carries no data we use; it only tells the blocker that the app came to the front.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> null
            else -> return
        }
        if (signal != null && processor.process(signal)) {
            val countedAt = System.currentTimeMillis()
            scope.launch {
                repository.recordReel(app, countedAt)
                val total = repository.todayTotal()
                // The lock starts right after the reel that reaches the limit.
                mainScope.launch { presenter?.onReelCounted(app, total) }
            }
        }
        presenter?.onTrackedEvent(app)
    }

    /** Reads only the scrolled view's class name and resource id. Never its text. */
    private fun scrollSignal(app: TrackedApp, e: AccessibilityEvent): ReelSignal.Scroll {
        val className = e.className?.toString()
        val source = e.source
        val viewId = try {
            source?.viewIdResourceName
        } finally {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) source?.recycle()
        }
        if (debuggable) Log.d(DIAG_TAG, "scroll pkg=${app.packageName} class=$className viewId=$viewId")
        return ReelSignal.Scroll(app, e.eventTime, className, viewId)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        presenter?.shutdown()
        presenter = null
        mainScope.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /**
         * Debug builds only: logs class names and view ids of scrolls, so testers can find the right
         * ids for `assets/surface_rules.json` (`adb logcat -s ReelSurfaceDiag`). Nothing is stored.
         */
        const val DIAG_TAG = "ReelSurfaceDiag"
    }
}
