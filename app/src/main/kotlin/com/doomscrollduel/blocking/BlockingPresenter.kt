package com.doomscrollduel.blocking

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import com.doomscrollduel.blocking.unlock.AskResult
import com.doomscrollduel.blocking.unlock.UnlockRepository
import com.doomscrollduel.domain.analytics.Analytics
import com.doomscrollduel.domain.analytics.AnalyticsEvent
import com.doomscrollduel.domain.analytics.LockKind
import com.doomscrollduel.domain.analytics.UnlockResultToken
import com.doomscrollduel.domain.blocking.BlockAction
import com.doomscrollduel.domain.blocking.BlockReason
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.feature.blocking.AskUi
import com.doomscrollduel.feature.blocking.OverlayActions
import com.doomscrollduel.feature.blocking.OverlayUi
import com.doomscrollduel.tracking.model.TrackedApp
import com.doomscrollduel.tracking.service.ReelScreenProbe
import java.util.EnumMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Does what [BlockingController] decides: sends the user back, shows the overlay, keeps it fresh, handles the
 * buttons. It lives and dies with the accessibility service and runs on the main thread.
 *
 * It decides nothing itself; every rule is in the controller. It only needs to know WHEN the user opened or left
 * a reel screen, which it finds out by checking the screen (see [ReelScreenProbe]) when the tracked app sends an
 * event, and only while something could block (`controller.isWatching()`).
 */
class BlockingPresenter(
    private val service: AccessibilityService,
    private val controller: BlockingController,
    private val probe: ReelScreenProbe,
    private val unlock: UnlockRepository,
    private val trustedTime: TrustedTime,
    private val scheduler: BlockingScheduler,
    private val scope: CoroutineScope,
    private val analytics: Analytics,
) {
    private val askUi = MutableStateFlow(AskUi.Idle)
    private val audio = AudioFocusHolder(service)
    private val screenOpen = EnumMap<TrackedApp, Boolean>(TrackedApp::class.java)
    private var currentApp: TrackedApp? = null
    private var lastProbeAt = 0L
    private var overlayShownAt = 0L
    private var tick: Job? = null
    private val lastWindowEventDay = EnumMap<LockKind, Long>(LockKind::class.java)

    private val overlay = OverlayController(
        service,
        OverlayActions(
            onRehneDo = { leave() },
            onContinue = { if (controller.continueGate()) hideOverlay() },
            onDismissBlock = { dismissBlock() },
            onAskFriend = { askFriend() },
        ),
    )

    /** Any accessibility event from a tracked app. Cheap when nothing could block. */
    fun onTrackedEvent(app: TrackedApp) {
        if (!controller.isWatching()) {
            if (overlay.isShowing) hideOverlay()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastProbeAt < PROBE_THROTTLE_MS) return
        lastProbeAt = now
        handle(app, probe.isOpen(service, app))
    }

    /** A reel was counted. If it hit the limit, the lock has started and the user is thrown out of the reels at once. */
    fun onReelCounted(app: TrackedApp, dayTotal: Int) {
        if (!controller.onReelCounted(dayTotal)) return
        analytics.track(AnalyticsEvent.LockTriggered(LockKind.TIMER, lengthHoursOfTimerLock()))
        scheduler.schedule()
        screenOpen[app] = true
        currentApp = app
        act(controller.onReelScreenSeen(), app, justOpened = true)
    }

    fun shutdown() {
        hideOverlay()
        scope.coroutineContext[Job]?.cancel()
    }

    // ----- what the screen is doing ------------------------------------------------------------------

    private fun handle(app: TrackedApp, open: Boolean) {
        val wasOpen = screenOpen[app] == true
        screenOpen[app] = open
        if (open) currentApp = app
        when {
            open && !wasOpen -> act(controller.onReelScreenOpened(), app, justOpened = true)
            open -> act(controller.onReelScreenSeen(), app, justOpened = false)
            wasOpen -> controller.onReelScreenClosed()
        }
    }

    private fun act(action: BlockAction, app: TrackedApp, justOpened: Boolean) {
        when (action) {
            BlockAction.Allow -> hideOverlay()
            is BlockAction.Block -> {
                val firstTime = !overlay.isShowing || overlay.ui.value !is OverlayUi.Block
                if (firstTime) trackWindowBlock(action.reason)
                showBlock(action)
                if (justOpened || firstTime) goBack(app)
                startTicker()
            }
            is BlockAction.Gate -> {
                if (!overlay.isShowing) overlayShownAt = SystemClock.elapsedRealtime()
                overlay.show(OverlayUi.Gate(action.state))
                audio.hold()
                startTicker()
            }
        }
    }

    private fun showBlock(action: BlockAction.Block) {
        if (!overlay.isShowing) overlayShownAt = SystemClock.elapsedRealtime()
        overlay.show(OverlayUi.Block(action.reason, action.remainingMs, action.canAskFriend, action.askQuotaLeft, askUi.value))
        audio.hold()
    }

    // ----- leaving the reels ---------------------------------------------------------------------------

    /** Back first (the least rude). If the reel screen is still there a moment later, Home. */
    private fun goBack(app: TrackedApp) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        scope.launch {
            delay(ESCALATE_AFTER_MS)
            if (overlay.isShowing && probe.isOpen(service, app)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun leave() {
        val app = currentApp
        hideOverlay()
        if (app != null) goBackQuietly(app)
    }

    /** Back or Home without showing anything: the user pressed "Rehne do". */
    private fun goBackQuietly(app: TrackedApp) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        scope.launch {
            delay(ESCALATE_AFTER_MS)
            if (probe.isOpen(service, app)) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun dismissBlock() {
        val app = currentApp
        hideOverlay()
        // If the reel screen is somehow still there, do not leave the user on it.
        if (app != null && probe.isOpen(service, app)) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    // ----- keeping the overlay fresh -----------------------------------------------------------------------

    private fun startTicker() {
        if (tick?.isActive == true) return
        tick = scope.launch {
            while (overlay.isShowing) {
                delay(TICK_MS)
                refreshOverlay()
            }
        }
    }

    private fun refreshOverlay() {
        if (!overlay.isShowing) return
        val app = currentApp
        val open = app != null && probe.isOpen(service, app)
        val action = if (open) controller.onReelScreenSeen() else controller.peek()
        val visibleFor = SystemClock.elapsedRealtime() - overlayShownAt
        if (askUi.value == AskUi.Waiting && !controller.status().askPending && action is BlockAction.Block) {
            askUi.value = AskUi.Ended
        }
        when (action) {
            BlockAction.Allow -> hideOverlay()
            is BlockAction.Block ->
                // Once the user has been sent back and has read the card, let them use the rest of the app.
                if (!open && visibleFor >= MIN_VISIBLE_MS) hideOverlay() else showBlock(action)
            is BlockAction.Gate -> if (!open) hideOverlay() else overlay.show(OverlayUi.Gate(action.state))
        }
    }

    private fun hideOverlay() {
        overlay.hide()
        audio.release()
        askUi.value = AskUi.Idle
        tick?.cancel()
        tick = null
    }

    // ----- ask a friend ----------------------------------------------------------------------------------------

    private fun askFriend() {
        scope.launch {
            val status = controller.status()
            val buddy = status.settings.buddy?.takeIf { status.settings.friendUnlockEnabled }
            when {
                buddy == null -> {
                    askUi.value = AskUi.Failed
                    return@launch
                }
                status.askPending -> {
                    askUi.value = AskUi.Waiting
                    return@launch
                }
                status.askQuotaLeft <= 0 -> {
                    askUi.value = AskUi.QuotaUsed
                    return@launch
                }
            }
            askUi.value = AskUi.Sending
            refreshOverlay()
            val answer = unlock.request(buddy!!.uid)
            analytics.track(AnalyticsEvent.UnlockRequested(unlockResultToken(answer)))
            askUi.value = when (val result = answer) {
                is AskResult.Sent -> {
                    trustedTime.update(result.serverNowMs)
                    controller.onUnlockRequested(result.requestId)
                    scheduler.schedule()
                    AskUi.Waiting
                }
                AskResult.NoNetwork -> AskUi.NoNetwork // never reached the server, so it does not use up a request
                AskResult.QuotaUsed -> AskUi.QuotaUsed
                AskResult.AlreadyPending -> AskUi.Waiting
                AskResult.NotSignedIn -> AskUi.NotSignedIn
                AskResult.NoBuddy, AskResult.Failed -> AskUi.Failed
            }
            refreshOverlay()
        }
    }

    /** `lock_triggered` for a bedtime or focus window: at most once per window kind per local day, not on every blocked reel. */
    private fun trackWindowBlock(reason: BlockReason) {
        val kind = when (reason) {
            BlockReason.BEDTIME -> LockKind.BEDTIME
            BlockReason.FOCUS -> LockKind.FOCUS
            BlockReason.STRICT_LOCK -> return // already sent when the timer-lock started
        }
        val today = java.time.LocalDate.now().toEpochDay()
        if (lastWindowEventDay[kind] == today) return
        lastWindowEventDay[kind] = today
        analytics.track(AnalyticsEvent.LockTriggered(kind))
    }

    /** The configured timer-lock length in hours, or null for "until midnight". */
    private fun lengthHoursOfTimerLock(): Int? =
        (controller.status().settings.lockLength as? com.doomscrollduel.domain.challenge.lock.LockLength.Hours)?.hours

    private fun unlockResultToken(result: AskResult): UnlockResultToken = when (result) {
        is AskResult.Sent -> UnlockResultToken.SENT
        AskResult.NoNetwork -> UnlockResultToken.NO_NETWORK
        AskResult.QuotaUsed -> UnlockResultToken.QUOTA_USED
        AskResult.AlreadyPending -> UnlockResultToken.ALREADY_PENDING
        AskResult.NotSignedIn -> UnlockResultToken.NOT_SIGNED_IN
        AskResult.NoBuddy -> UnlockResultToken.NO_BUDDY
        AskResult.Failed -> UnlockResultToken.FAILED
    }

    private companion object {
        const val PROBE_THROTTLE_MS = 400L
        const val ESCALATE_AFTER_MS = 900L
        const val TICK_MS = 1_000L
        const val MIN_VISIBLE_MS = 2_500L
    }
}
