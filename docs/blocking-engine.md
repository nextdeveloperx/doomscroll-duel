# Blocking engine

## Design in one paragraph
All decisions live in a pure-Kotlin core (`domain/blocking/BlockingController`) that depends only on four ports
(clock, time zone, settings, state store). The Android side is thin glue: the accessibility service tells the
controller "a reel screen is open", the controller answers Allow / Block / Gate, and `BlockingPresenter` shows the
overlay and sends Back/Home. **Nothing relies on a timer firing.** Every decision is computed from timestamps at the
moment a reel screen is seen, so a killed process, a reboot or Doze cannot leave the user unlocked by accident.
WorkManager (`BlockingScheduler`) only does housekeeping: it wakes at the next lock/window boundary to refresh the
Home pill and notifications, and re-arms after reboot.

## Priority order
1. Friend pass (15 min after a friend approves) — lifts the timer lock **and** bedtime/focus windows.
2. Strict timer-lock
3. Bedtime / Focus window (reel limit is zero inside the window)
4. Wait-10 gate
5. Allow

## Lock clock (survives kill, reboot, clock changes)
Stored state keeps the lock's *progress* in ms, not an end timestamp. Progress advances by:
- same boot: monotonic `elapsedRealtime` difference (user changing the clock or time zone has no effect);
- after reboot: trusted server time if we have an anchor, else wall-clock difference (never negative).
Known gap: with no trusted time, moving the wall clock forward *while the phone is off/rebooting* can shorten a lock.
Trusted time is refreshed from every server response (unlock requests, sync).

## Settings commitment rule (`SettingsPolicy`)
Tightening is always allowed. Loosening is refused while a lock/window is running: Strict Lock off, limit raise
(takes effect tomorrow anyway), changing/removing the unlock friend, editing or disabling the running bedtime/focus
window. Wait-10 is always changeable.

## Friend unlock
Client asks `requestUnlock` (Cloud Function). Server enforces: friendship, 3 requests per rolling 24 h, one pending
at a time, 30 min expiry. The friend gets an FCM data message and a notification with Approve / Deny
(`respondUnlock`). The requester gets `unlock_response`; the pass length is 15 min minus the time since approval, so
a late push does not hand out a longer pass. A request that never reached the server (no network) is not counted.

## Files
- Core: `domain/blocking/*`, `domain/challenge/lock/StrictLock.kt`
- Persistence: `data/prefs/DataStoreBlockingStores.kt` (DataStore Preferences holding JSON via `BlockingCodec`;
  damaged JSON falls back to defaults)
- Android glue: `blocking/*`, `tracking/service/ReelScreenProbe.kt`, `ReelAccessibilityService.kt`
- Overlay UI: `feature/blocking/BlockingOverlay.kt`
- Settings: `feature/settings/*`
- Server: `functions/src/index.ts`, `functions/src/unlockRules.ts`, `firebase/firestore.rules`

## Privacy
The service still reads no content. The only additions: the scrolled view's class name and layout view id (already
approved), a "is this view id on screen" probe in `ReelScreenProbe` (reads nothing), and Back/Home global actions.
`scripts/check-service-privacy.sh` enforces that only `ReelScreenProbe` may touch `rootInActiveWindow`.
