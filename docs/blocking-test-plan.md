# Blocking engine test plan

Automated: `domain/blocking/*Test.kt` (361 tests in the pure suite incl. earlier modes) and
`functions/src/unlockRules.test.ts`. Everything below marked **[device]** has NOT been run yet and must be.

## Device reboot
1. Start a 2 h Strict Lock, reboot after 30 min, boot. **[device]** Lock continues with ~1 h 30 m left (progress
   advanced by trusted/wall time across the downtime). Opening Instagram Reels is blocked.
2. Reboot with airplane mode on, set clock +12 h while off. Known gap: lock may end early. Verify the app shows the
   result of the documented behaviour; with trusted time cached it must not shorten.
3. After reboot, `BootReceiver` re-schedules the WorkManager boundary job; Home pill is correct without opening the app.
4. Friend pass active at reboot: remaining pass time keeps counting, never extends.

## App killed / swiped / force-stopped
- Swipe from recents mid-lock: state is in DataStore; accessibility service restarts and still blocks.
- Force stop: accessibility service is disabled by Android. Health card shows Accessibility OFF; lock is not enforced
  until re-enabled (cannot be prevented, stated honestly in UI copy).

## No network
- Ask friend with no network: shows "net nahi hai", does NOT use a request from the 3/day quota.
- Friend approves but requester offline: FCM delivers when back; pass length = 15 min minus lateness; if > 15 min late
  → no pass.
- Strict lock itself never needs network.
- Trusted time absent: lock runs on the monotonic clock; only the reboot caveat above applies.

## Time-zone and clock changes
- Fly across zones mid-lock: lock unaffected (monotonic). Bedtime 23:00–06:00 follows the *new* local time.
- Change time zone while inside a bedtime window so it is now outside: window ends (local-time semantics);
  `TIMEZONE_CHANGED` triggers re-schedule. **[device]**
- User sets clock back 2 h during lock: no extra time gained or lost.
- DST start/end inside a bedtime window: window length follows wall clock (unit-tested with ZonedDateTime).
- Midnight rollover: daily limit/quota roll; focus ranges belong to the day they start.

## Bedtime / Focus
- Overlapping focus ranges are rejected; Sunday→Monday wrap checked.
- Editing/disabling a window while it runs is refused; editable after it ends.
- Limit is zero only inside the window: Wait-10 gate off, reels blocked, brain state unchanged.

## Wait-10 gate **[device]**
- Countdown 10 s with breathing animation; "Rehne do" exits the reel app via Home.
- Leaving and returning within 3 s keeps the session; longer starts a new gate.
- Reduced-motion on: breathing becomes static.

## Block overlay **[device]**
- First action Back; if the reel screen is still there after 900 ms, Home. No loop of overlays.
- Audio focus requested so the video pauses. Overlay must disappear when the lock ends while shown.
- Per app: Instagram, YouTube Shorts (view ids verified), Facebook Reels and Snapchat Spotlight (no ids known:
  expect heuristic detection, verify manually).

## Permission health **[device]**
- Each of Accessibility, Battery optimization, Notifications shows live status on return from system settings
  and its Fix button opens the right screen. Revoking notifications mid-session flips the chip.
- OEMs (Xiaomi, Oppo, Vivo, Samsung): autostart/battery killers; follow the battery guide.

## Server **[not run]**
- Deploy functions to an emulator; run `@firebase/rules-unit-testing` for `unlockRequests` and `fcmTokens` rules.
- Only the chosen friend can respond; stranger gets permission-denied; second respond gets failed-precondition.
