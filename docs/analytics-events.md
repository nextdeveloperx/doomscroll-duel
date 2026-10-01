# Analytics and crash reporting spec

Tools: **Firebase Analytics** (events) and **Firebase Crashlytics** (crashes). Both are OFF until the person taps
"Haan" on the one-time "Anonymous data" card (or turns the switch on in Settings > Account and privacy). Choosing "Nahi"
or leaving it undecided sends nothing. Turning it off later stops collection and asks Firebase to reset the analytics data.

## What is never sent
No name, username, email, phone number, account ID, friend or opponent identity, dare text, photo, reel title or any
content, exact reel count, exact time or location. No advertising ID (the permission is removed in the manifest and the
ad consent flags are denied). `setUserId` is never called. Crash reports contain stack traces only; exception messages are
stripped before they are recorded (`CrashReporter`).

Every event passes `AnalyticsPolicy` first: the event name and each parameter name must be in the table below, and every value
must be a short lowercase token (`a-z`, `0-9`, `_`, at most 40 characters, no run of six or more digits). Anything else is dropped
and, in debug builds, logged as a warning. Numbers are replaced by buckets.

## Events

| Event | Fires when | Parameters | Wired in the app today |
|---|---|---|---|
| `onboarding_completed` | The first-run flow ends | `steps_skipped` | No: there is no onboarding screen yet. Call `AnalyticsEvent.OnboardingCompleted` when it exists |
| `permission_granted` | A permission turns from off to on while we watch (not on launch) | `permission` | Yes: Accessibility, Notifications and Battery, via `PermissionAnalytics` |
| `duel_created` | The person sends a new duel invite | `mode`, `duration_hours`, `entry_coins`, `limit_bucket` | Yes at the button (the invite is not stored on a server yet) |
| `duel_accepted` | The person accepts someone's invite | `mode`, `accept_delay` | No: there is no accept screen yet |
| `duel_finished` | The result screen is shown for a finished duel | `mode`, `result`, `reels_bucket`, `over_limit` | Yes at the result screen (sample data until duels are saved) |
| `lock_triggered` | A lock stopped the person: the timer-lock started, or a bedtime or focus window blocked a reel screen (at most once per window per day) | `kind`, `length` | Yes |
| `unlock_requested` | The person asked a friend to lift a lock | `result` | Yes |
| `paywall_viewed` | The paywall screen opens | `trigger` | Yes |
| `subscription_started` | A new purchase is confirmed by our server (not a restore) | `plan` | Yes |

Event names follow Firebase rules (letters, digits, underscores, at most 40 characters, none start with `firebase_`,
`google_` or `ga_`).

## Parameters and their only allowed values

| Parameter | Values |
|---|---|
| `steps_skipped` | `0`, `1_2`, `3_plus` |
| `permission` | `accessibility`, `notifications`, `battery_unrestricted` |
| `mode` | `duel`, `squad`, `night_pact`, `forfeit_dare`, `strict_lock` |
| `duration_hours` | `6`, `24`, `168`, `other` |
| `entry_coins` | `0`, `25`, `50`, `100`, `other` |
| `limit_bucket`, `reels_bucket` | `0`, `1_20`, `21_50`, `51_100`, `101_200`, `201_plus` |
| `accept_delay` | `lt_1h`, `1_6h`, `6_24h`, `gt_24h` |
| `result` (duel_finished) | `win`, `loss`, `draw`, `forfeit_win`, `forfeit_loss`, `cancelled`, `expired` |
| `over_limit` | `true`, `false` |
| `kind` | `timer`, `bedtime`, `focus` |
| `length` (timer only) | `1h`, `3h`, `midnight` |
| `result` (unlock_requested) | `sent`, `quota_used`, `already_pending`, `no_buddy`, `no_network`, `not_signed_in`, `failed` |
| `trigger` | `direct`, `unlimited_duels`, `squad_battle`, `strict_lock`, `analytics`, `custom_schedules`, `brain_skins` |
| `plan` | `monthly`, `yearly` |

The only **user property** is `plan` = `free` or `pro`.

## Questions these events answer (the closed beta and after)
- Activation funnel: app opened -> `permission_granted` (accessibility) -> first `duel_created` -> `duel_finished`.
- Does the disclosure scare people off? Compare disclosure views (Play Console + `permission_granted` rate).
- Is the lock used? `lock_triggered` per active user per week, split by `kind`.
- Friend unlock health: `unlock_requested` split by `result` (a high `no_network` or `failed` share is a bug).
- Monetisation: `paywall_viewed` by `trigger` -> `subscription_started` by `plan`.
Revenue, refunds and churn come from Google Play Console, not from these events.

## Testing
1. Unit tests: `AnalyticsTest` (privacy gate, buckets, consent) and `AnalyticsSpecDocTest` (this file matches the code).
2. On a debug build: `adb shell setprop debug.firebase.analytics.app com.doomscrollduel.debug`, then Firebase console >
   DebugView. Check every event above shows the listed parameters and nothing else.
3. Crashlytics: tap the hidden "Test crash" row in the debug build (Settings, debug only) and confirm the report arrives with
   no message text. Remove nothing from release: the row does not exist there.
4. Confirm that with the card answered "Nahi" no events appear in DebugView.
