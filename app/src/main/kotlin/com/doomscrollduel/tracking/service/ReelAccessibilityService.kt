package com.doomscrollduel.tracking.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.view.accessibility.AccessibilityEvent
import com.doomscrollduel.blocking.BlockingPresenter
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.blocking.TrustedTime
import com.doomscrollduel.blocking.unlock.UnlockRepository
import com.doomscrollduel.domain.analytics.Analytics
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.legal.AccessibilityConsent
import com.doomscrollduel.domain.legal.ConsentStore
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.tracking.detector.SurfaceRulesHolder
import com.doomscrollduel.tracking.detector.ReelEventProcessor
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
 *  - Nothing is processed until the person has tapped Agree on the in-app disclosure ([AccessibilityConsent]).
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
    @Inject lateinit var consent: ConsentStore
    @Inject lateinit var analytics: Analytics

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
            analytics = analytics,
        )
        scheduler.schedule()
        scope.launch { repository.ensureToday() }
        // Best effort: Android may refuse to start a foreground service from the background.
        TrackingKeepAlive.start(this)
    }

    private val pipeline by lazy {
        ReelEventPipeline(
            processor = processor,
            // The person must have agreed to the in-app disclosure first. Switching the service on in Android's own
            // Accessibility list is not enough: without that agreement every event is dropped before it is looked at.
            consent = { AccessibilityConsent.mayProcessEvents(consent.current()) },
            record = { app, at ->
                repository.recordReel(app, at)
                repository.todayTotal()
            },
            // The lock starts right after the reel that reaches the limit.
            onCounted = { app, total -> mainScope.launch { presenter?.onReelCounted(app, total) } },
            scope = scope,
            debugLog = debuggable,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        pipeline.onEvent(event)?.let { app -> presenter?.onTrackedEvent(app) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        presenter?.shutdown()
        presenter = null
        mainScope.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
