# Google Play review: rejection risks and status

Legend: **DONE** = in the repo and checked by a test or script. **YOU** = only you can do it (accounts, money, legal,
hosting). **GAP** = the app is missing something Play will look for; fix before you submit.

## A. Launch blockers (fix before the first submission)

| # | Item | Status |
|---|---|---|
| A1 | **Report and block buttons** for people and dare proofs. The Terms and the content-rating answer promise them; Play's User Generated Content policy requires them in the app. The model has "report" but no screen exists | **GAP** |
| A2 | **Firebase sign-in** (Google or phone) and an **age confirmation** ("I am 16 or older") before an account is created. Without sign-in, friends, duels, Pro purchase and account deletion cannot work | **GAP** |
| A3 | Real **public URLs** for privacy policy, terms and deletion page, hosted and put in `legal_links.xml` and Play Console | **YOU** |
| A4 | **Support mailbox** you really read, and the 7-day / 30-day promises in the policy | **YOU** |
| A5 | **Sync of Dare proofs**: the 7-day auto-delete must exist as a scheduled function before photos ship (the policy promises it) | **GAP** (do before dare proof upload ships; otherwise remove proof photos from the policy and Data safety) |
| A6 | Target API level: new apps and updates must target the API level Google currently requires (check Play Console > Policy status; as of late 2026 it is Android 16 / API 36) | **YOU** |
| A7 | Deploy and test `deleteAccount`, `verifyPurchase`, Firestore and Storage rules on a real Firebase project | **YOU** |

## B. Accessibility API (the strictest review)

| Risk | What Google checks | Status |
|---|---|---|
| No prominent disclosure | Full-screen, in-app, before the permission, plain words, says what data, how used, not shared | **DONE** (`DisclosureScreen`, strings in `disclosure_strings.xml`) |
| Implied consent | Must need a tap on Agree; no auto-dismiss; Back must not count as agree | **DONE** (Back = Not now; nothing recorded) |
| Service works without consent | A user could enable it in Android Settings directly | **DONE** (service drops every event until consent; banner asks for it) |
| Declared as accessibility tool | A wellbeing app must not set `isAccessibilityTool="true"` | **DONE** (false; the guard checks) |
| Reads content | Reviewers decompile; any `getText`/tree walking is a red flag | **DONE** (`scripts/check-service-privacy.sh`) |
| Too many packages | Service limited to what it needs | **DONE** (4 packages) |
| Video and form | Declaration form and a video that shows disclosure, consent, the feature | **YOU** (script in `accessibility-declaration.md`) |
| Disclosure text drift | Text in the app, store listing, policy and video must agree | **DONE** (pinned hash; bump `DISCLOSURE_VERSION` when it changes) |
| Cannot get out | App prevents uninstall or turning the service off | **DONE** (it does not; Strict Lock only blocks switching off *inside* the app, and the Terms and disclosure say the service can be turned off in Android Settings) |
| Description mentions the API | Store description must say the app uses the Accessibility API and why | **DONE** (`store-listing.md`) |

## C. Misleading claims and metadata

| Risk | Status |
|---|---|
| Health or medical claims ("cure addiction", "improve mental health", "detox") | **DONE** in all text in the repo (scan in `check-play-readiness.sh`; "Brain ko Pro treatment do" was changed). Keep it that way in screenshots, graphics and ads |
| Overclaiming accuracy ("exactly counts every reel") | **DONE** (listing says counts may sometimes be off) |
| Trademarks / impersonation: Instagram, YouTube, Facebook, Snapchat names and logos | **DONE** names only, with a "not affiliated" line; **YOU**: do not use their logos in screenshots or the icon |
| App name "Doomscroll Duel" is a working name | **YOU**: confirm availability |
| Keyword stuffing, emoji in title, "free", "#1", testimonials | **DONE** in listing text; **YOU** in graphics |
| Screenshots not from the real app | **YOU** |
| Fake ratings or reviews | Never do this |
| Data safety form does not match behaviour | **YOU** (answers in `data-safety.md` written from the code; re-check when anything changes) |
| Privacy policy missing Accessibility section, or link broken | **DONE** text; **YOU** hosting |

## D. Gambling and betting wording

Google's Real-Money Gambling policy applies to real money and prizes. This app has none. Reviewers and IARC still react
to gambling words and to anything that looks like paying to win or turning coins into value.

| Risk | Status |
|---|---|
| Words "bet", "wager", "stake", "jackpot", "odds", "winnings" in the app or listing | **DONE**: UI says "Entry coins"; scan in `check-play-readiness.sh` covers app strings and the listing |
| Coins bought with money | **DONE**: no product sells coins; `check-coin-money-separation.sh` |
| Coins cashed out, gifted for money, or traded | **DONE**: no such feature; guard scans for cash-out wording |
| Pro giving coins or an edge in winning | **DONE**: Pro gives no coins and no bonus; duel entry is the same for everyone |
| Chance-based outcome (random rewards, loot boxes) | **DONE**: none. Keep it so. Outcome depends only on reel counts |
| Content rating "gambling" | **DONE**: answered No with reasons (`content-rating.md`) |
| Marketing that mentions "win", "earn money", "rewards" next to prices | **YOU**: say "virtual coins", never "earn money" |
| Entry coins can be lost to a friend | Fine: they are earned virtual points, said clearly on New Duel ("Asli paisa nahi, cash-out nahi") |

## E. User-generated content (dares, proofs, roasts)

| Risk | Status |
|---|---|
| Free-text insults or harassment between users | **DONE**: roast stickers and dares are fixed lists written by us; there is no free chat |
| Dares that are dangerous or sexual | **DONE**: curated list of 16, checked by `check-dare-strings.sh`; the loser may skip, the winner may waive. **YOU**: review the list once for local sensitivities |
| Proof photo or video | Optional, private between two players, deleted after 7 days, 20 MB, image/video only (`storage.rules`) |
| In-app report and block | **GAP** (A1) |
| Terms of Use with objectionable-content rules | **DONE** text (`terms-of-use.md`); **YOU** host it and link it; the app also needs to show the Terms before first use (with A2) |
| Moderation process and response time | **YOU**: someone must read reports; the Terms promise action on serious reports within 24 hours, change the promise if you cannot keep it |
| Remove a user's content on request / on deletion | **DONE** on deletion (`deletion.ts`) |
| Predators and minors (16-17 users meeting strangers) | Friends only, by username or invite link; no search of strangers, no contact upload, no chat. Keep it that way |

## F. Subscription disclosures

| Risk | Status |
|---|---|
| Price not shown, or hard-coded | **DONE**: price text and the saving badge come from Google Play |
| Renewal and cancel terms hidden | **DONE**: line directly under the Buy button, longer text below, manage button in the app |
| Free trial wording | None offered. If you add one, show when it ends and the price after it |
| "Restore" missing | **DONE**: always visible on the paywall |
| Features sold as Pro were free in the description | **DONE**: description lists the free and the Pro features |
| Cancel in the app only | **DONE**: opens Google Play subscription page; the Terms and the delete flow explain that deleting an account does not cancel it |
| Paywall blocks core free features | **DONE**: counting, 3 duels a day, Night Pact, Dare and Wait-10 stay free |
| Subscription not acknowledged (auto-refund after 3 days) | **DONE**: the server acknowledges |
| Dark patterns (countdown timers, hidden close button) | **DONE**: none; "Baad mein" is as large as "Pro dekho" |

## G. Account deletion and privacy

| Risk | Status |
|---|---|
| In-app deletion missing | **DONE** (`DeleteAccountScreen`, `deleteAccount` function) |
| Web deletion link missing | **YOU** host `web/delete-account.html`; put the address in Data safety |
| Deletion incomplete as features are added | **DONE**: a test fails when `firestore.rules` gets a collection that deletion does not handle |
| Policy says "no backups" | **YOU**: do not enable Firestore backups or PITR without updating the policy |
| Children | App is for 16+. See the age note below |
| Data collected beyond what is declared | **DONE** for Firebase Analytics and Crashlytics (optional, off until the person says yes, AD_ID removed; in the policy and `data-safety.md`). Any further SDK changes the form and the policy first |

### Age: 13 or 16, and what is safer for India
- Between 13 and 16, **16 is safer** for India. India's Digital Personal Data Protection Act, 2023 treats **anyone under
  18 as a child**, and (as I understand it) requires verifiable parental consent to process a child's data and forbids
  tracking or behavioural monitoring of children. Setting 13 puts every 13-17 year old in that regime. 16 reduces the
  number of children but does not remove the issue.
- **The cleanest line is 18+.** The App measures behaviour (reel counts), which is exactly what the Act restricts for
  children. If you target 16-30 you either (a) accept the risk for 16-17 year olds and get a lawyer's opinion, (b) add
  a parental-consent flow, or (c) launch 18+ first. My recommendation is (c) or (a) with legal advice.
- If you choose 18+, change "16" to "18" in the Privacy Policy section 2, the Terms section 1, the store listing, the
  Data safety "Families" answer stays No, and the target-audience form (18+ only). The age confirmation screen in A2 must
  match.
- Check the current status and dates of the DPDP Rules with a lawyer; they were being phased in and I cannot confirm
  today's dates from here.

## H. Permissions and technical

| Risk | Status |
|---|---|
| Unneeded dangerous permissions | **DONE** (`check-play-readiness.sh` fails on SMS, call log, contacts, location, SYSTEM_ALERT_WINDOW, QUERY_ALL_PACKAGES, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, READ/WRITE_EXTERNAL_STORAGE) |
| Foreground service type without declaration | **YOU**: declare "special use" with the manifest subtype and a short video |
| Notification permission asked at a bad time | **DONE**: asked from the Settings "Fix" button, not on first launch |
| `allowBackup` leaks local data | **DONE**: false |
| Crash on first start / ANR | **YOU**: run the manual checklist on real phones; nothing has run on a device yet |
| Pre-launch report and Play Integrity | **YOU** |
