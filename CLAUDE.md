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
5. Analytics and crash reports carry no personal data and stay OFF until the person says yes (`domain/analytics`,
   `docs/analytics-events.md`). Nothing sold through Google Play touches coins (`scripts/check-coin-money-separation.sh`).
6. The reel counter does nothing until the person agreed to the in-app Accessibility disclosure (`AccessibilityConsent`).

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
- **Nothing has run on a phone, an emulator, Gradle, Firebase or Google Play.** What did run here: 459 pure app tests, 31 server
  tests, four guard scripts, and a compile check against Compose Desktop (including the real compose-ui-test API) and real Android
  framework jars with Hilt, Room, DataStore, WorkManager, Firebase and Billing stubbed. UI and instrumented tests are
  compile-checked only. Google Maven is unreachable from this sandbox, so `gradle/libs.versions.toml` versions are unresolved.
- Firestore and Storage rules, all Cloud Functions (unlock, purchases, coins, deletion), the Play subscription mapping (field names
  and grace-period expiry unchecked against a live response) and the FCM flow are untested.
- Still fake: profile name/coins, the duel card's opponent count, Live and Result data (`feature/FakeData.kt`). `duel_created` and
  `duel_finished` analytics fire on that sample data.
- Launch blockers (see `docs/play/review-checklist.md`): Firebase sign-in, age confirmation, report/block screens, 7-day dare-proof
  auto-delete function, hosted privacy/terms/deletion pages and real addresses in `legal_links.xml`, onboarding screen
  (`onboarding_completed` event has no caller), accept-invite screen (`duel_accepted` has no caller), free 3-duels-a-day counter in
  the UI (rule and server gate exist), analytics screen (hourly data not stored), skin rendering and coin shop UI, squad lobby /
  night pact / dare flow screens, Room `night_counts` and `strict_lock` tables, remote update of `surface_rules.json`.
- Unverified view ids: Facebook Reels and Snapchat Spotlight have none; Instagram and YouTube ids are unverified (`verified: false`).
- Open decisions for the owner: minimum age (texts say 16; 18 is safer under India's DPDP Act), app name (working name), prices,
  Firebase region, support mailbox and grievance contact.

## Suggested next steps (in order)
1. Open the project in Android Studio, fix the first Gradle sync (versions), run `./gradlew :app:testDebugUnitTest` and lint.
2. Run `connectedDebugAndroidTest` on an emulator; fix what the UI and reel counter tests find.
3. Run `docs/device-test-matrix.md` on real phones; fill the real view ids for all four apps.
4. Firebase Auth (Google + phone) with an age confirmation, friends by username/invite link, Firestore sync with an outbox;
   deploy and emulator-test rules and functions.
5. Duel persistence and settlement functions using the same pure rules; wire the free-duel counter, `duel_accepted` and onboarding.
6. Report/block screens, dare-proof auto-delete, host the legal pages, then the closed beta (`docs/beta-plan.md`) and launch
   (`docs/launch-checklist.md`).

## Handy commands
- Privacy guard: `scripts/check-service-privacy.sh` (run before any commit touching `tracking/` or `blocking/`)
- Coins vs money guard: `scripts/check-coin-money-separation.sh`
- Dare strings match catalog: `scripts/check-dare-strings.sh`
- Play readiness guard: `scripts/check-play-readiness.sh` (`--release` before upload; `--update-hash` after deliberately changing
  the Accessibility disclosure text, and bump `AccessibilityConsent.DISCLOSURE_VERSION`)
- Legal pages: `python3 scripts/build-legal-pages.py` (needs `scripts/legal-config.json` from the template)
- Store images: `scripts/prepare-store-assets.sh <raw> <ready>` after `StoreAssetsTest`
- Unit tests: `./gradlew :app:testDebugUnitTest`; UI + instrumented: `./gradlew :app:connectedDebugAndroidTest`
- Server tests: `cd functions && npm test`
- Release bundle: `./gradlew :app:bundleRelease` (refuses to run without signing; see `docs/launch-checklist.md`)
