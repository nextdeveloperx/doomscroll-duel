# Launch checklist

Tick each box when it is really done. Items marked **(you)** need your accounts, money or a decision. Nothing here has been
done yet; the code and files it refers to exist in the repo.

## 0. Before anything: the project builds
- [ ] **(you)** Open the project in Android Studio. The Gradle files were written without being able to download from Google's
      repository, so the first sync may ask for version changes. Fix them in `gradle/libs.versions.toml` (keep the Kotlin, KSP
      and Compose-compiler versions matched), then run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug`.
- [ ] **(you)** Create the Firebase project (region Mumbai `asia-south1` for Firestore and Functions), add two Android apps:
      `com.doomscrollduel` and `com.doomscrollduel.debug`, download `google-services.json` into `app/` (git-ignored).
      Add the SHA-1 and SHA-256 of the **upload key and the Play app signing key** (Play Console > Setup > App signing) so
      Google sign-in and phone auth work in release builds.
- [ ] **(you)** Turn on Firebase Authentication (Google, Phone), Firestore, Storage, Functions, Cloud Messaging, Analytics,
      Crashlytics. Set Analytics **event data retention to 2 months** (Analytics > Data settings > Data retention) because the
      privacy policy says so. Disable Google Signals and ad personalisation.
- [ ] Deploy: `firebase deploy --only firestore:rules,storage,functions`; run `cd functions && npm test` first.
- [ ] CI is green on the `main` branch (`.github/workflows/ci.yml`).
- [ ] Run the instrumented tests on an emulator or phone: `./gradlew :app:connectedDebugAndroidTest`
      (Compose UI tests for Home, New Duel and Result, and the reel counter test).

## 1. App icon and screenshots in the app's own style
The graphics are drawn by the real app screens, so they always look like the app.
1. Run on an emulator or phone (any size): `./gradlew :app:connectedDebugAndroidTest --tests "com.doomscrollduel.store.StoreAssetsTest"`
2. Pull the files: `adb pull /sdcard/Android/data/com.doomscrollduel.debug/files/store ./store-raw`
3. Make them Play-ready and check every limit: `scripts/prepare-store-assets.sh ./store-raw ./store-ready`
4. Upload from `store-ready/`: `icon_512.png` (app icon), `feature_graphic_1024x500.png`, and `en/` plus `hi/` phone screenshots
   (6 each: Home, Battle modes, New duel, Live duel, Result, Pro).
- [ ] Look at every image: captions readable, nothing cut off, no real names or numbers (the screens use sample data).
- [ ] Do not put Instagram, YouTube, Facebook or Snapchat logos in any graphic. Do not write "#1", prices or "free coins".
- [ ] Nice to have: a proper single-colour layer for Android 13 themed icons (`<monochrome>` in `mipmap-anydpi-v26/ic_launcher.xml`).
- [ ] Optional: add a 7-inch and 10-inch tablet screenshot only if you want to be featured on tablets; otherwise skip.

## 2. Store listing text (English and Hinglish)
- [ ] Copy from `docs/play/store-listing.md`: app name, short description, full description, in **English (en-IN)** and the
      **Hinglish** blocks as a second language. The wording scan runs in CI (`scripts/check-play-readiness.sh`).
- [ ] Fill every placeholder `{{...}}` (see `docs/play/README.md`), host the three pages, and run
      `scripts/check-play-readiness.sh --release` until it passes.
- [ ] Play Console > App content: privacy policy URL, Data safety (`docs/play/data-safety.md`), Accessibility declaration and video
      (`docs/play/accessibility-declaration.md`), foreground service declaration, content rating (`docs/play/content-rating.md`),
      target audience (16+ or 18+, see the age note in `docs/play/review-checklist.md`), **Ads: no ads**, account deletion URL.
- [ ] **App access** (for Google's reviewers): give a working test account and say exactly how to reach the features: sign in with the
      test account, tap PADHO, Agree, enable the service in Android Settings. Without this the review stalls.
- [ ] Contact details in Play Console (email and a postal address are shown publicly for paid apps and subscriptions).
- [ ] Subscription `doomscroll_pro` with base plans `monthly` and `yearly` created and **active**, prices set in INR **(you decide)**,
      grace period and account hold turned on, license testers added.

## 3. Signing
Google Play App Signing is on by default for new apps. You keep an **upload key**; Google keeps the **app signing key**.
1. Make the upload key once, on your computer, and back it up in **two** places (password manager + an offline copy). If you lose
   it you can ask Play to reset the upload key, but it takes days.
   ```
   keytool -genkeypair -v -keystore doomscroll-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy `keystore.properties.example` to `keystore.properties` (git-ignored) and fill it. On CI use the environment variables
   `DUEL_KEYSTORE_FILE`, `DUEL_KEYSTORE_PASSWORD`, `DUEL_KEY_ALIAS`, `DUEL_KEY_PASSWORD` (store them as secrets, never in the repo).
3. The build **refuses** to make a release bundle without signing, so you cannot upload an unsigned file by accident.
- [ ] `git status` shows no `.jks` or `keystore.properties`; `git log -p -S storePassword` shows no real password.

## 4. R8 (shrinking) and the release build
`isMinifyEnabled` and `isShrinkResources` are on for release; rules are in `app/proguard-rules.pro`.
1. Build: `./gradlew :app:bundleRelease` (output `app/build/outputs/bundle/release/app-release.aab`).
2. Install the release build on a real phone (bundletool, or `./gradlew :app:installRelease` with signing set up) and run the **whole**
   happy path: disclosure, enabling the service, counting, Wait-10, Strict Lock, a friend unlock, the paywall and a test purchase,
   restore, deleting an account. R8 breaks reflection and serialization silently, so a debug-only test proves nothing.
3. Watch `adb logcat` for `ClassNotFoundException`, `NoSuchMethodError`, "Serializer for class ... is not found" or
   Hilt "Missing binding". Fix with a narrow `-keep` rule and a comment saying why.
4. Confirm crash reports are readable: force a test crash in a debug build and in a release build and see the de-obfuscated stack in
   Crashlytics (the Crashlytics plugin uploads the mapping; the AAB also carries it for Play).
5. Confirm Android Settings still lists the accessibility service under the app's name (its class name is kept on purpose).
- [ ] App size looks sensible (target under 25 MB download).

## 5. Version codes
`versionName` is `MAJOR.MINOR.PATCH`. `versionCode = MAJOR*1,000,000 + MINOR*10,000 + PATCH*100 + BUILD` (set in
`gradle.properties`: `VERSION_MAJOR/MINOR/PATCH/BUILD`; CI may pass `-PbuildNumber=`).

| Release | versionName | VERSION_BUILD | versionCode |
|---|---|---|---|
| Closed test first build | 1.0.0 | 1 | 1,000,001 |
| Closed test fix 1 | 1.0.0 | 2 | 1,000,002 |
| Closed test fix 2 | 1.0.1 | 1 | 1,000,101 |
| Production 10% | 1.0.1 | 2 | 1,000,102 |
| First feature update | 1.1.0 | 1 | 1,010,001 |

Rules: every upload to any Play track needs a **higher** versionCode than every earlier upload; a number is used up the moment it is
uploaded, even to the internal track; BUILD, MINOR and PATCH are each limited to 0 to 99 (the build fails clearly if not): raise PATCH
when BUILD would pass 99. Never lower a number. Tag the git commit for each upload (`v1.0.1-2`).

## 6. Pre-launch checks
- [ ] Internal test with 3 people: installs and starts.
- [ ] Closed test done: 14 days, 12+ testers (20 invited), go criteria met (`docs/beta-plan.md`), production access granted.
- [ ] Play pre-launch report (runs on its own on the uploaded build): fix crashes, permission and accessibility warnings.
- [ ] Device matrix done on the must-devices (`docs/device-test-matrix.md`), no open S1.
- [ ] Policy checklist: all items marked **YOU** and **GAP** in `docs/play/review-checklist.md` are closed (report/block buttons,
      sign-in and age confirmation, 7-day dare-proof deletion, URLs, mailbox).
- [ ] Cloud: Firestore and Storage rules deployed; Functions deployed; a budget alert on the Firebase/Google Cloud billing account
      (for example Rs 2,000 and Rs 5,000) so a bug cannot run up a bill silently; App Check considered for later.
- [ ] Support: mailbox read daily, FAQ page ("counter not counting", battery guide per maker, delete account, cancel subscription),
      reply templates ready.
- [ ] Rollback plan understood (section 8).

## 7. Staged rollout at 10 percent
1. Play Console > Production > Create new release > upload the AAB > release notes (English and Hinglish) > **Rollout percentage 10%**.
   Start with **India only** under Countries/regions; add countries later when the privacy text and support cover them.
2. Wait for review (it can take several days for a new app that uses the Accessibility API; do not plan a date around it).
3. Increase only while the numbers below hold, always one step at a time:

| Day after release | Rollout | Move up only if |
|---|---|---|
| 0 | 10% | Crash-free users 99% or better and ANR under 0.47% after the first 24 hours with at least 100 sessions |
| 2 to 3 | 25% | No new S1 issue; reviews average 4.0 or better; count and lock complaints under 5% of reviews and mails |
| 4 to 5 | 50% | Same, and the cloud bill is as expected |
| 7 | 100% | Same, and a full week of monitoring below is clean |

**Halt the rollout** (Production > Manage rollout > Halt) if any of these happen: crash-free users below 98%, a crash or ANR cluster
affecting more than 2% of sessions, the counter or a lock failing silently on a common phone, payment or sign-in broken, a privacy
problem, or a policy warning from Google. Halting stops new installs of that version; it does not remove it from people who have it.

## 8. If something goes wrong
- You cannot roll back a published version. **Fix forward**: build the fix with a higher versionCode and roll it out to 100%
  (you can do this in hours; review for a small update is usually quick).
- A bad Firestore rule or Function: roll back with `firebase deploy` of the previous commit; Functions and rules are not tied to app versions.
- Wrong counting in one app (view ids changed after Instagram or YouTube updated): ship an updated `assets/surface_rules.json`
  in a new build. (Remote update of the rules is a planned feature and is not built yet.)
- Subscription problem: Play Console > Orders; refund or fix; the entitlement function re-checks every 6 hours and on every
  Play notification.
- Account or data complaint: answer within 7 days (the policy promises it), delete through the same function as the app.

## 9. First-week monitoring routine
Fifteen minutes each morning (and at 8 pm for the first three days). Write one line per day in a shared note.

| # | Look at | Where | Healthy | Act if |
|---|---|---|---|---|
| 1 | Crash-free users and sessions, new crash clusters | Crashlytics dashboard; Play Console > Android vitals | 99%+; no new cluster above 0.5% of users | New cluster: open the stack, reproduce, fix forward. Above 2%: halt the rollout |
| 2 | ANR rate and user-perceived crash rate | Play Console > Android vitals (Google's "bad behaviour" lines are about 0.47% ANR and 1.09% crash overall, and 8% on one phone model; check the exact numbers shown in Play Console) | Well below the lines | Near a line: find the phone model and screen causing it |
| 3 | Reviews and ratings | Play Console > Ratings and reviews | Average 4.0+; no repeated complaint | Reply to every review within 24 hours, politely and with the fix path; the same complaint three times becomes a task |
| 4 | Counting problems | Support mail + reviews with words like "not counting", "band ho jata" | Under 5% of mails and reviews | Find the maker (matrix); update the battery guide; check `ReelSurfaceDiag` ids for the app that changed |
| 5 | Permission funnel | Firebase: `permission_granted` (accessibility) vs installs | 60%+ of installs reach it | Low: read disclosure feedback; the disclosure or the Android prompt is scaring people |
| 6 | Activation and retention | Firebase Analytics: `duel_created`, D1 and D7 retention (Play Console > Statistics) | D1 above 30% | Low: check onboarding and the first-duel path |
| 7 | Locks | `lock_triggered` by `kind`; `unlock_requested` by `result` | `failed` and `no_network` under 10% | High: check Functions logs and FCM delivery |
| 8 | Payments | Play Console > Monetize > Subscriptions and Orders; `paywall_viewed` to `subscription_started` | Purchases verified, none stuck pending | Pending or refunded oddities: check `verifyPurchase` logs |
| 9 | Cloud health and cost | Firebase console: Functions errors, Firestore reads, Authentication; Cloud Billing report | No error spikes; cost as forecast | Errors: read logs (only hashed ids are logged); cost spike: find the query, add an index or limit |
| 10 | Privacy and policy mail | Support mailbox, Play Console policy status | Nothing | Deletion requests: do them the same week; any Google policy message: read it the same day |

End of week: write a one-page note: numbers, what broke, what you fixed, whether to go to the next rollout step. Then plan the
first update (1.0.1) from the real problems, not from new ideas.
