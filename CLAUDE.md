# Doomscroll Duel (placeholder name): project memory for Claude Code

Android app. Friends challenge each other to watch fewer short videos. An AccessibilityService counts reels in
Instagram, YouTube Shorts, Facebook Reels and Snapchat Spotlight. The friend with the lower count wins virtual
coins. A cartoon brain gets "fried" as the count climbs.

Users: Indian students and young adults (16 to 30), mid-range Android phones. UI copy is simple Hinglish
(Hindi in English letters mixed with English). Keep every user-facing string in `res/values/strings.xml`.

## Stack
Kotlin, Jetpack Compose with a fully custom theme (no Material look, no material3), MVVM with Hilt, Coroutines
and Flow, Room, Firebase Auth / Firestore / Cloud Functions / FCM. Min SDK 26. One `:app` module, package
`com.doomscrollduel`, namespace must be `com.doomscrollduel`.

## HARD RULES (never break these)
1. Coins are virtual only. No real money, no cash-out, no betting, coins are never for sale.
2. Reel counting uses an AccessibilityService and must never read, store or upload what the user watches.
   Allowed per event: package name, event type, event time, and for scroll events only the scrolled view's class
   name and layout resource id (e.g. `reel_recycler`). NEVER getText, contentDescription, parent/child walking,
   usernames, captions. `scripts/check-service-privacy.sh` enforces this; run it before every commit that touches
   `tracking/`.
3. Everything the user sees must work offline first and sync later (Room is the source of truth for the UI).
4. Production quality. No placeholder TODOs unless the user asks.

## Working style the user expects
- Do the work, run what can be run, report outcomes faithfully (say what was NOT verified).
- Keep answers short and plain. Flag decisions that are the user's to make instead of guessing silently.
- Commit to `main` of https://github.com/nextdeveloperx/doomscroll-duel and push when work is done.

## What exists (all pushed)
1. **Design system** `core/designsystem/`: tokens (Color/Type/Shape/Theme), chunky components (ChunkyButton,
   ChunkyCard, StatChip, HpBar, ToggleSwitch, StepperButton, ChoiceChip, BottomNavBar, ModeTile, TrackingBanner,
   VsBadge, Avatar, StatusPill, FighterPanel, DuelScreen...), `BrainView` (vector brain: HAPPY up to 40 percent of
   the limit, FRIED 41 to 99, ZOMBIE 100+; HP = 100 - percent). Outline 3dp ink #0B0620, hard 6dp shadow, press
   moves down 4dp, reduced-motion respected, 48dp touch targets, text contrast >= 4.5:1 (tested).
   Fonts bundled: Lilita One, Nunito (OFL, see `licenses/`).
2. **Six screens** with previews + `navigation/NavGraph.kt`: Home, Battle modes, New duel, Live duel, Result,
   Settings, plus Battery guide. Designed at 390x844dp, scale via `DuelScreen`.
3. **Real reel counter** `tracking/`: `ReelAccessibilityService` (thin) feeding the pure `ReelEventProcessor`
   (swipe = scroll on a Shorts/Reels pager then content change; two counted reels never closer than 600 ms),
   `SurfaceRules` from `assets/surface_rules.json`, foreground service + quiet notification, accessibility-off
   detection with a banner, battery-optimization guide with Xiaomi/Realme/Oppo/Vivo/Samsung steps, Room storage
   (`reel_counts`: date, package, count, updated_at; new local day = new row = midnight reset), streak,
   `HomeViewModel` exposing live count and brain state as Flow.
4. **Friends (rules, models, logic only)**: Username, InviteCode/InviteLinks, PhoneNumber validation,
   repository interfaces, `firebase/firestore.rules` (friend's count readable only while a duel is active).
5. **All five challenge modes as pure Kotlin** `domain/challenge/` with unit tests: 1v1 DUEL, SQUAD BATTLE (Pro),
   NIGHT PACT, FORFEIT DARE, STRICT LOCK (Pro). Full rules in `docs/challenge-modes.md`.

Other docs: `docs/manual-test-checklist.md`, `docs/privacy-data-flow.md`, `docs/data-model.md`.

## Key decisions already made (do not re-litigate)
- Duel winner = LOWER final reel count, even if both pass the limit. Equal = tie, stakes returned. The limit does
  not change the winner. Forfeit: accessibility service OFF for MORE than 10 minutes in a row (exactly 10:00 is
  fine); a dead phone is never punished. Both forfeit: whoever crossed first loses; same instant = refund.
- Escrow at accept time, zero-sum, ledger ops have stable ids so settling twice cannot pay twice.
- Invites expire after 24 hours. Terminal states refuse every event.
- Surface check uses class name + view id (user approved). Instagram and YouTube ids in
  `assets/surface_rules.json` are UNVERIFIED guesses; **Facebook and Snapchat have NO ids yet and count nothing
  until found on a real phone** (see section 0 of the manual test checklist).
- Streak: consecutive days under the daily limit; a day with no row (counter not running) ends it. Default daily
  limit 100.
- Squad Battle = two squads of 3 to 10, lower average wins (exact integer comparison), then lower worst member.
- Night Pact: 23:00 to 06:00 local per member; counter off > 10 min or no report = night broken.
- Forfeit Dare: curated catalog only (16 dares), loser may skip, winner may waive, either can report, proof private
  and deleted after 7 days. Strict Lock cannot stop the user turning the service off; say so honestly.
- Sign-in will be Google or Indian phone number. Friends by username or invite link, NO contact upload.

## Blocking engine (turn 6) - built
Pure core `domain/blocking/*` + glue `blocking/*`, overlay `feature/blocking`, Settings fully wired, Focus hours
screen, Cloud Functions `functions/` (requestUnlock/respondUnlock), rules for unlockRequests/fcmTokens.
See `docs/blocking-engine.md` and `docs/blocking-test-plan.md`. 361 pure tests pass; full Android code compile-checked
against shims only. Not run on a device. Unlock needs sign-in + friends (BuddyDirectory is empty until then).
Decisions to review: friend pass also lifts windows; rolling 24 h quota; window edits locked while running; Pro gating
of Strict Lock not enforced (no billing yet).

## Monetization (turn 7) - built
Pro subscription (`doomscroll_pro`, base plans monthly/yearly) + virtual coin economy. See `docs/billing.md`,
`docs/billing-test-plan.md`, `docs/coin-economy.md`. HARD RULE kept: coins are never sold, never given by Pro, never
cashed out; `scripts/check-coin-money-separation.sh` enforces it. Entitlement is written ONLY by Cloud Functions
(`verifyPurchase`, `playNotifications`, `refreshStaleEntitlements`); the app reads `entitlements/{uid}` via
`EntitlementService` (cache + clock-tamper fail-closed). `BillingManager` never grants Pro. Pro never removes a
protection already set up (`SettingGate`). Pure tests: 418 app + 22 server pass. NOT verified: nothing ran on a device,
Play, or Firebase; billing-ktx/Firestore/Hilt only shim-compiled; subscriptionsv2 field names unchecked against a live
response; grace-period expiry semantics open (see billing.md). Not wired yet: free 3-duels/day counter in the UI (needs
duel persistence; server gate + pure rule exist), analytics screen (per app / per hour; hourly data is not stored yet),
skin rendering in BrainView, coin shop UI, Firebase sign-in (purchases need it).

## Google Play review pack (turn 8) - built
`docs/play/` has the Privacy Policy, Terms, deletion page, Data safety, Accessibility declaration + video script, content
rating, store listing and the rejection checklist (`review-checklist.md`, read its launch blockers). Code: Accessibility
disclosure screen (`feature/legal`, text in `res/values/disclosure_strings.xml`, pinned by hash in
`docs/play/disclosure.sha256`); the service drops every event until `AccessibilityConsent` is valid (version 1; bump it
when the text changes and run `scripts/check-play-readiness.sh --update-hash`); in-app Delete account
(`feature/account`, `account/`, function `deleteAccount` in `functions/src/deleteAccount.ts`, plan + registry in
`deletion.ts` with a test that fails when `firestore.rules` gets a collection deletion does not handle). Guards:
`scripts/check-play-readiness.sh` (`--release` refuses placeholders and the `.invalid` web address),
`scripts/build-legal-pages.py` (makes `web/*.html`). UI wording: "Stake" is now "Entry coins". Tests: 427 app + 31
server pass; compile-checked against shims only. NOT built: report/block screens, sign-in, age confirmation, 7-day
dare-proof auto-delete function, hosting of the pages. Minimum age in the texts is 16; see the age note (18 is safer
for India's DPDP Act).

## Launch readiness (turn 9) - built
Gradle project now exists (`settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, wrapper 8.14.3,
signing from `keystore.properties` or env vars, R8 on, versionCode = MAJOR*1e6+MINOR*1e4+PATCH*100+BUILD from
`gradle.properties`, CI in `.github/workflows/ci.yml`). It was written WITHOUT being able to resolve Google Maven, so the first
sync will likely need version tweaks; nothing has been built by Gradle. Tests: property/fuzz unit tests for settlement, brain
state, lock timer and coin ledger (`domain/properties`); Compose UI tests for Home, New Duel, Result and an instrumented reel
counter test (real AccessibilityEvent objects -> `ReelEventPipeline` -> Room) under `app/src/androidTest`; `StoreAssetsTest`
makes the Play icon, feature graphic and EN/HI screenshots from the real screens (`scripts/prepare-store-assets.sh` makes them
Play-ready). Compile-checked against real Compose test APIs on desktop; NEVER RUN (no emulator). Analytics: nine events,
anonymous tokens only (`domain/analytics`, spec `docs/analytics-events.md`, parity test), off until the person taps Haan, no
user id, AD_ID removed, Crashlytics via `CrashReporter` (messages stripped). Docs: `docs/device-test-matrix.md`,
`docs/beta-plan.md` (20 testers, 14 days, 8-question form), `docs/launch-checklist.md` (assets, signing, R8, versions, 10% staged
rollout, week-1 routine). Tests: 459 pure app tests + 31 server tests pass here. `onboarding_completed` and `duel_accepted` have
no screen yet (spec only).

## What is NOT verified or NOT built yet
- (Gradle project now exists, see Launch readiness.) Original dependency notes: compose-bom, ui, foundation, animation,
  ui-tooling-preview, ui-text, navigation-compose, hilt (+ hilt-navigation-compose), room, lifecycle-runtime-compose,
  activity-compose, kotlinx-serialization-json, coroutines, junit + coroutines-test. NO material3.
- Nothing has run on a real phone or in Android Studio. Code was compile-checked only against Compose Desktop and
  real Android 14 framework jars with Hilt/Room/Lifecycle stubbed. Pure-logic tests (235) really pass.
- Firestore rules untested (no emulator was available).
- Still fake: profile name/coins, the duel card's opponent count (see `feature/FakeData.kt`).
- Not built: Firebase sign-in + Firestore implementations, Cloud Functions for settle/expire/tick (unlock functions exist, undeployed), screens for squad
  lobby / night pact / dare flow / strict lock, Room tables `night_counts` and `strict_lock` (+ migration 1 to 2),
  photo/video proof upload, onboarding with the Play accessibility disclosure, Strict Lock enforcement (needs a
  Play declaration decision), Play Billing for Pro.

## Suggested next steps (in order)
1. Gradle scaffolding (settings, version catalog, AGP, Hilt, KSP, Room schema export) so it builds in Android Studio;
   run the unit tests for real; fix whatever the first real build finds.
2. Run the manual test checklist on a real phone; find and fill the view ids for all four apps.
3. Onboarding with the full-screen accessibility disclosure and notification permission.
4. Firebase Auth (Google + phone), friends by username/invite link, Firestore sync with an outbox.
5. Cloud Functions for duel settlement/expiry using the same rules, plus emulator tests for `firestore.rules`.
6. Screens for the remaining modes.

## Handy commands
- Privacy guard: `scripts/check-service-privacy.sh`
- Dare strings match catalog: `scripts/check-dare-strings.sh`
- Unit tests: `./gradlew :app:testDebugUnitTest`; UI + instrumented: `./gradlew :app:connectedDebugAndroidTest`
- Play readiness guard: `scripts/check-play-readiness.sh` (`--release` before upload)
