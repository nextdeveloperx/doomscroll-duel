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
   name and layout resource id (e.g. `reel_recycler`), and for window-change events only the class name of the screen that came
   to the front (e.g. Facebook's full-screen reel viewer `ImmersiveActivity`; owner asked for the Facebook fix, Oct 2026). NEVER getText, contentDescription, parent/child walking,
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
- Sign-in is Google (phone number later). Friends by username, invite link, or opt-in Contacts match: contact EMAILS are SHA-256 hashed ON THE PHONE and only the hashes are sent (owner decision, Oct 2026; this replaced the earlier "no contact upload" rule). Never send names, numbers or raw addresses.

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

## Login, username, friends, contacts, invite push (Oct 2026) - built, NOT deployed
App: `feature/auth` (login page with "Abhi nahi", username page), `feature/friends` (Dost screen, invite accept, notifications,
push-token registrar), `data/auth`, `data/social`, `domain/social`. Google sign-in uses Credential Manager and needs the Firebase
Google provider on plus the debug SHA-1 registered (done for `com.doomscrollduel.debug` in project brainpal-b3119).
Server code in `functions/src/social.ts` (+ `socialRules.ts`, `push.ts`): checkUsername, completeProfile, createInvite,
acceptInvite, addFriendByUsername, matchContacts, inviteUser. 37 function tests pass. NOT deployed: the Firebase project is the
shared brainPAL one, Cloud Firestore is not even enabled there yet, and Functions need the Blaze plan. Until then the Dost screen
shows "Server abhi taiyaar nahi hai". Contacts: emails are SHA-256 hashed on the phone (`EmailHash`), only hashes are sent; the
server compares with `users/{uid}.emailHash` written from the verified token. Invite web host is `invite_web_host` in strings.xml
(brainpal-b3119.web.app) - nothing is hosted there yet, so https invite links will not open the app until a page and assetlinks exist.

## People list, invites inbox, notifications (Oct 2026) - built, rules DEPLOYED, functions NOT deployed
Firestore rules are live on brainpal-b3119 (deployed 2 Oct 2026) and tested on the emulator (`firebase/rules-test`, 20 checks, needs JDK 21).
Cloud Functions cannot be deployed yet (Cloud Functions API answers 403: the project is not on the Blaze plan), so this works with no function:
- `directory/{uid}` = username + display name of EVERYONE who chose a username, readable by any signed-in person (owner decision: the Log
  list shows all users). No email, hash or counts in it. This changes the earlier "usernames cannot be listed" rule: update the Privacy Policy
  and Data safety answers before launch.
- Invite = the sender writes `users/{target}/inbox/{senderUid}` (one per sender, name checked against the directory by the rules). The target
  accepts in the app, which writes both friend edges in one batch; rules allow that only while the invite is waiting. This replaces "friend edges
  only by Cloud Function" while functions are off; the functions still work and use the same collections.
- UI: Dost page has Invites / Log / Dost tabs (`feature/friends`), `data/social/FirebasePeopleRepository`, `InboxNotifier` (phone notification
  while the app process is alive, which the foreground counter service keeps true), deep link `doomscrollduel://friends`.
- `functions/src/inboxPush.ts` (`onInviteInboxCreated`) sends the real push when the app is closed. Written and unit-compiled, NOT deployed.
- Still needs functions: invite links (createInvite/acceptInvite), contacts matching, `deleteAccount`.

## Real battles and contacts search without Cloud Functions (Oct 2026) - built, rules DEPLOYED, tested on emulator only
- Battle = `duels/{id}` (players, status pending|active|finished|declined|cancelled, reelLimit, hours, stakeCoins, startAt; the end is startAt + hours).
  Challenge needs an existing friend edge; only the invited player can accept/decline; either player can finish after the end.
  `duels/{id}/counts/{uid}` = each player's reels SINCE THE START (not "today"); readable by the two players while active and after finish.
  `DuelTracker` (runs in the app process) takes the start line (all-time total per app at accept / first sight), uploads the count, finishes
  the duel, and raises the challenge / "accepted" notifications. Screens: NewDuel (real friends) -> Live(duelId) -> Result(duelId), Home card.
- COINS ARE NOT MOVED: wallets are server-only, so Result says so (`coinsSettled = false`). Escrow/settlement still needs Cloud Functions
  (Blaze plan) using the pure rules in `domain/challenge/duel`. Counts are written by each phone, so a modified app could lie about its own count.
- Contacts: `emailIndex/{sha256(email)}` -> `{uid}`, readable one document at a time (no list). Each person registers their own hash once they have a
  username (`PeopleRepository.ensureSelfListed`). The rules cannot recompute a hash, so someone could register another person's hash; the effect is a
  wrong match in that contact's list. The server `matchContacts` function is the private alternative once functions can be deployed.
- Rules tests: `firebase/rules-test` (42 checks, needs JDK 21).

## Broadcast voice rooms (Oct 2026) - built, rules DEPLOYED, voice between TWO phones NOT tested
- OPEN voice rooms (since Oct 2026; first built friends-only): `broadcasts/{room}` (host, title, seenAt heartbeat), `members/{uid}` (name, username, seenAt), short-lived `signals`
  (offer/answer/ice, deleted when read). Audio and chat go phone to phone over WebRTC (`io.getstream:stream-webrtc-android`), a mesh (one call per pair,
  smaller uid offers). Chat is NEVER stored on a server: it only travels over the data channels and is kept in `BroadcastChatStore` (SharedPreferences, 300 lines/room).
- Only public STUN servers are configured; there is no TURN relay, so some strict networks will stay on "judd raha hai". Add a TURN server in
  `VoiceMesh.createPeer` if the beta shows that. `BroadcastService` (foreground, microphone type) keeps a call alive in the background.
- ANYONE signed in can join ANY live room and the list shows every room with its head-count; the host can remove a person (a `kicked` document stops them coming back). A person asks others in through `users/{uid}/broadcastInvites/{fromUid}` (one per sender, 30 s apart; push-style notification via `InboxNotifier`, deep link `doomscrollduel://broadcast/join/{room}`). There is NO report/block yet: with strangers in voice rooms that is a Play-policy launch blocker (user-generated content) and the age limit (16) needs a decision. A person who closes the app without leaving disappears from the room after ~70 s.
- Several battles can run at once: Home shows the newest, the Battle tab lists all ("Meri battles"), and Live has "Battle chhodo" (status `cancelled`).
- Rules tests: 61 checks in `firebase/rules-test` (needs JDK 21).

## Battle results and voice indicators (Oct 2026)
- No manual "Result dekho" any more: when a battle's time is over the Live screen opens the result by itself, `DuelTracker` posts a "battle khatam"
  notification (deep link `doomscrollduel://result/{id}`), and Home / the Battle tab keep the finished battle ("Khatam · tum jeete") until its result is opened.
- Broadcast: the profile circle pulses (two green rings) while a person talks. Own voice = loudness of the microphone samples; others = WebRTC `audioLevel`
  of the playing stream. Only a loudness number is read; nothing is recorded.
- OPEN DECISION (owner): ads inside Reels/Shorts are counted like reels. Telling them apart needs a signal from the ad itself, and rule 2 allows only the
  scrolled view's class name and view id on scroll events. Options: allow the view id of an ad marker on content-change events (ids must be found on a phone
  while an ad is showing), a manual "-1 ad tha" outside battles, or leave it.
- Fixed (Oct 2026): voice never connected because the offer-direction check was reversed (an offer must come from the SMALLER uid). `VoiceMeshLoopbackTest`
  (instrumented; run with `:app:installDebugAndroidTest` then `adb shell am instrument -w -e class com.doomscrollduel.broadcast.VoiceMeshLoopbackTest
  com.doomscrollduel.debug.test/androidx.test.runner.AndroidJUnitRunner`; do NOT use `connectedDebugAndroidTest`, it uninstalls the app and wipes the login)
  connects two engines in one process, passes chat both ways and sees audio packets flow. Real microphone audio between two phones is still untested.

## Broadcast reliability + Facebook (Oct 2026)
- Broadcast: signals are cleared BEFORE the member document is written (the old order could delete the first offer), calls stuck "connecting"
  for 15 s are rebuilt, chat lines carry an id and are passed on phone to phone (a phone that is not directly connected to the writer still
  gets them), join steps run in parallel with the profile cached. The host leaving sets `closed` on the room (rules allow only the host, only
  `closed: true`); everyone else leaves with "Host ne room band kar diya", and a host silent for 120 s counts as gone.
- TURN relay: none configured. Set TURN_URLS / TURN_USERNAME / TURN_CREDENTIAL in `local.properties` (see `data/broadcast/IceServers.kt`); the
  shared openrelayproject credentials are refused by the server. Without a relay voice fails between phones that cannot reach each other.
- Facebook: the Reels tab and the feed give the SAME event names/ids, but a reels pager snaps one whole page per swipe, so the scroll distance
  (`AccessibilityEvent.scrollDeltaY`, a signed number, API 28+) of one swipe adds up to the same total every time (measured 2120 px on a 2412 px
  phone, differences under 0.1 percent) while feed scrolls vary. `surface_rules.json` marks Facebook `pagerByDistance`; the processor counts a swipe
  when its total matches a recent one within 1.5 percent (the first swipe of a session only teaches the size) and `flush` closes it shortly after the
  last scroll. Full-screen reel viewer (`ImmersiveActivity`) counts through the class-name rule. Measured on a phone: Reels tab forward swipes count,
  back swipes do not, 8 feed swipes counted 0. Owner approved the extra signals (window class name, scroll distance), Oct 2026.
- Direction (all apps): only a swipe to the NEXT reel counts. The first scroll event of a swipe that reports a distance sets the direction; negative
  = back to the previous reel = not counted. Phones that report no distance count as before.

## Real coin wallet, leaderboard, scroll-tracking investigation, floating counter redesign, daily-limit sync fix (Oct 2026)
First real device testing of the whole app (one Motorola phone, Android 15) happened this round. A lot of earlier "verified"
claims needed correction once actually run on a phone, see below.

- **Coins move for real now, without Cloud Functions** (still not on Blaze). `firebase/firestore.rules`: `wallets/{uid}/ops/{opId}`
  is an append-only ledger (hold/release/transfer_credit/transfer_debit, `create`-only per fixed opId so a retry or both phones
  racing cannot pay twice); `directory/{uid}` gained `weeklyCoins`/`weeklyPeriodKey`/`monthlyCoins`/`monthlyPeriodKey` (self-write
  only, a period-key change is the only way the number may drop). 83 rules-emulator checks pass (`firebase/rules-test`, JDK 21),
  deployed. `domain/coins/WalletRepository` + `data/coins/FirebaseWalletRepository` apply the existing pure `DuelSettlement`
  hold/payout ops as these Firestore ops; wired from `DuelTracker` (hold on active, settle on finished) and `ResultRoute`
  (defensive second settle attempt). Same trust tier the reel counts already had: a modified app can misreport its own side but
  cannot invent coins. A forfeit still settles as a tie (the tracking gap that would prove one is never written to Firestore).
- **Leaderboard** inside the Progress tab (`feature/progress/LeaderboardSection.kt`+`LeaderboardViewModel.kt`,
  `domain/social/LeaderboardRepository`, `data/social/FirebaseLeaderboardRepository`): Dost (friends, resets weekly) and India
  (everyone, resets monthly) boards, ranked by coins actually won (`transfer_credit` ops only). Top-3 medal styling, own row
  highlighted, "find my rank" via a `count()` aggregate when off the visible list. Not live-updating; a re-entry refetches.
  Progress tab reordered to Leaderboard, then the three stat cards, then the chart (owner request).
- **Scroll tracking, corrected on real device (do not trust an old `verified` flag without re-checking)**: Instagram and
  YouTube Shorts (`reel_recycler`) both genuinely work, but **YouTube Shorts fires its scroll accessibility event only
  sometimes** — measured 7-47% of real swipes across several sessions, same device and app version, no code difference
  between runs. A real, externally-caused limitation (under-counts, never zero-counts), not a bug here. A candidate second
  signal was prototyped read-only and NOT wired to production counting: `TYPE_WINDOW_CONTENT_CHANGED` with `contentChangeTypes`
  bit `0x40` (the literal value actually observed on device — it does not match `AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED`'s
  real constant, a bug caught mid-investigation; match the measured bit, not the name). `ReelEventPipeline.paneAppearedDiag`
  logs "would-count" with an 800 ms correlation window, debugLog-gated only; needs explicit owner privacy approval plus more
  multi-session validation before ever being promoted to real counting. Snapchat Spotlight's real view id is
  `lenses_explorer_feed_recycler_view` (added to `surface_rules.json`, `verified: false`), confirmed different from the camera
  screen's lens carousel, but just as session-inconsistent as YouTube (one session 29/29 swipes counted, another 0/8, service
  confirmed alive both times). Facebook not re-tested (owner said it already works; out of scope unless a regression shows up).
- **Floating reel counter redesigned** (`feature/counter/FloatingCounter.kt`, `tracking/service/ReelCounterBadge.kt`): warm
  cream pill, brain mascot instead of a dot, bold dark-brown count, `statusBarsPadding()` + `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
  so it clears the status bar/cutout. New states: ACTIVE (just counted, full opacity) -> IDLE (no new count for ~2.5s, fades to
  a faint but still-visible alpha, never fully hidden) -> back to ACTIVE on the next count; TARGET_REACHED (today's count >=
  the real configured limit, never hardcoded) locks full opacity, no idle fade. The old "remove the whole overlay after 12s of
  silence" behaviour is gone — the window now only comes down when the tracked app leaves the foreground or the counter is
  off in Settings. Verified live on device against the reference design for ACTIVE/IDLE; TARGET_REACHED reasoned through code
  review only (could not lower the live-configured limit fast enough on the test device to force it), not device-verified.
- **Daily-limit dial bug, fixed**: `feature/settings/SettingsScreen.kt`'s `LimitCard` read the dial straight from
  `settings.dailyLimit` (the raw field; a lower limit lands there at once but a RAISE waits in `pendingLimit` until its day
  arrives and is only ever resolved at read time via `BlockingSettings.limitOn(today)`, never merged back into `dailyLimit`).
  Once a pending raise's day had arrived, every other screen correctly showed the new number while the dial kept showing the
  stale pre-raise one. Fixed by computing `activeLimit = settings.limitOn(today)` once and passing it to both the summary and
  the dial; the pending-raise text under the dial now only shows while that raise's day has not arrived yet. Confirmed live on
  device (610 vs 610, dial and summary moving together on a `-` tap). Not a new rule, a UI-wiring fix; `limitOn` itself already
  had unit coverage.
- All 480 unit tests pass throughout; nothing here changed the detection privacy contract. Real-device testing happened on one
  phone/Android version only.

## Floating counter repositioned twice: below the cutout, then Dynamic-Island-style hugging it (Oct 2026)
- First pass: the pill sat right against the status bar/punch-hole (only an 8dp fixed `y`, and `Modifier.statusBarsPadding()`
  on a window added straight via `WindowManager` does nothing — it isn't part of an Activity's decor view, so Compose never
  gets a real inset there). Fixed in `ReelCounterBadge.kt` by computing the real status-bar+cutout inset in code
  (`WindowManager.currentWindowMetrics.windowInsets`, API 30+; the `status_bar_height` resource below that) and using that as
  the window's own `y`, once. Confirmed live on Instagram and YouTube Shorts on the Motorola.
- Second pass (owner wanted an iPhone-Dynamic-Island look, not a badge floating well below the cutout): `FloatingCounter.kt`
  slimmed to a 34dp pill (24dp icon, 16sp semibold count, 3dp shadow, `defaultMinSize` so "610"/"1,000" never clip).
  `ReelCounterBadge.kt` now also centers the pill on the PHYSICAL cutout's own x-position, not just the screen's middle:
  `cutoutCenterXPx()` reads `displayCutout.boundingRectTop` (API 30+ via `currentWindowMetrics`; the deprecated
  `Display.getCutout()` on 28-29); the window is full-width with the pill placed via `Box(fillMaxWidth) { align(TopCenter)
  .offset(x = ...) }`, so an off-center punch-hole still gets hugged and a cutout-less device falls back to true
  screen-center. The clearance margin below the cutout dropped from 40dp to 6dp to close the gap the owner flagged.
- Setting up an emulator in this environment hit a real limit along the way (not device-related): the background-task
  runner's own low-memory guard killed both a system-image download (at 1GB RAM free) and the emulator boot itself (at
  4.6GB free afterward) — not simply a RAM-headroom problem this environment can work around. An AVD (`reel_test`, Pixel 6
  profile, API 35 google_apis x86_64) and its system image ARE installed at `F:\AppData\Local\Android` for whoever opens it
  directly from Android Studio's own AVD Manager instead of through this background-task path.
- Confirmed live on the Motorola once reconnected: Instagram Home feed AND the Reels tab both show the slim cream pill
  snug below the status bar/cutout, centered on the punch-hole, not overlapping Instagram's own "Reels" title, back arrow,
  "+" icon or any status-bar icons; count updated 328->329 on a real swipe; the idle fade still visibly dims the pill a few
  seconds after the last count, same as before this redesign (no regression in the ACTIVE/IDLE state machine).

## Floating counter position, corrected again: the pill lives IN the status-bar row, not below it (Oct 2026)
- The "below the cutout, 6dp margin" placement from the previous entry still collided with Instagram's own Reels title —
  measured with a temporary `Log.d("ReelBadgeDiag", ...)` in `ReelCounterBadge.attach()` (removed again once the fix was
  confirmed): `density=2.125` (the OEM's overridden density, not the raw 400 `wm density` reports), `windowY=118px`, pill
  height `72px` at that density, so the pill spanned y=118-190px while Instagram's own header starts flush at y~106px and
  is ~100px tall — direct overlap, regardless of how small the margin was. Root cause: on a phone where an app draws its
  own chrome right up against the status bar (Instagram's Reels screen does), there is NO row below the status bar that is
  both close to the cutout and free of that app's header — closing the gap and avoiding the header were mutually exclusive
  as long as the pill stayed below the inset.
- Real fix: the pill now sits INSIDE the status-bar/cutout band itself (`ReelCounterBadge.topOffsetPx`, vertically centered
  within that band when a real cutout exists: `y = (inset - pillHeightPx) / 2`, clamped to >=0) rather than below it. That
  band is otherwise empty next to a centered punch-hole (clock sits far left, battery/signal icons far right; measured gap
  ~570px either side on this phone against a ~280-320px pill), so it both hugs the cutout with zero gap AND never reaches
  the row where any tracked app's own header lives. Falls back to the old below-the-status-bar placement (with a small
  margin) only when there is no real cutout to hug (`cutoutCenterXPx() == null`), since on those phones the status-bar
  band is usually too short to fit a 34dp pill inside at all. `FloatingCounter.PILL_HEIGHT` was made non-private so this
  math uses the pill's real height instead of a second guess at the same number.
- Verified live on the Motorola (API tap-confirmed via `dumpsys window | grep mCurrentFocus` before every screenshot, per
  the standing rule in this file): both the full immersive Reels feed and the "Reels" title-header view now show the pill
  sitting cleanly in the status-bar row, centered on the cutout, with the entire header area below it empty — no overlap
  with "Reels", the back arrow, the "+" icon, or the clock/battery/signal icons. Compile, 480 unit tests, and
  `assembleDebug` all still pass; no detection/counting/coin logic touched, `FloatingCounter.kt`'s fade/target-reached
  state machine untouched (only `ReelCounterBadge.kt`'s window-y math and `PILL_HEIGHT`'s visibility changed).
