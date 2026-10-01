# Closed beta plan: 20 friends, 14 days

## Google's rule (check it before you start, it has changed before)
Google Play asks **new personal developer accounts** to run a **closed test** before they can apply for production
access. As I understand the current rule, you need **at least 12 testers opted in, continuously for 14 days**. Use 20 so a
few people dropping out or opting out does not reset the clock. Testers must opt in through the closed-test link (not
just be on a list), have the app installed, and stay opted in. After day 14 you apply for production access in Play Console
and answer questions about the test (what you tested, the feedback you got, what you changed): the notes in this plan are
written so you can answer from them. Confirm the numbers in Play Console > Testing > Closed testing before you recruit.
Organisation accounts are not subject to this rule; if you have one, the plan still works as a quality gate.

## Who to invite (20 people)
- 20 friends who really use short-video apps, 16 or older (the app is 16+; do not invite anyone younger).
- At least 8 on **different phone makers** than yours: Samsung, Redmi/Poco, Realme, Oppo, Vivo (see `device-test-matrix.md`).
- At least 6 pairs of friends who will duel each other (the app needs friends: 10 pairs is better than 20 singles).
- A mix of Android 10 to 15, and at least 3 phones with only 3 to 4 GB of RAM (where makers kill apps the fastest).
- 2 people who are happy to be "reporters": they message you the moment something is odd.

## Before day 0 (about 3 days)
1. Closed test track created, **internal test** first with 3 people to catch a broken install.
2. Public pages live (privacy policy, terms, delete-account) and linked in the app and in Play Console.
3. Data safety, content rating, target audience, ads = none, app access instructions filled (see `docs/play/`).
4. A Google Group or an email list of the 20 testers added to the closed track; the opt-in link sent.
5. Firebase: Analytics event retention set to 2 months, Crashlytics on, DebugView checked, an alert to your email on new
   crash clusters (Firebase console > Crashlytics > alerts).
6. A support WhatsApp group for the 20, and the feedback form (below) ready.
7. A license-tester account added in Play Console so subscriptions can be tried without paying.
8. Build 1.0.0 (versionCode 1000001) uploaded to the closed track; install tested on two phones.

## The 14 days
| Day | What happens | What you do |
|---|---|---|
| 0 | Testers opt in and install. Each person turns the counter on and does the **count check** (below) | Confirm 20 opted in. Share the "first 10 minutes" message |
| 1 | First duels between pairs. Ask everyone to try Wait-10 and Strict Lock once | Read Crashlytics; fix S1 bugs the same day (release 1.0.1 to the same track) |
| 2 | Bedtime mode that night | Check who had the counter stop overnight (B3 in the matrix) |
| 3 | Friend unlock: each pair tries asking and approving | Record notification delay (N1 to N5) |
| 4 to 6 | Normal use. One small ask per day in the group (see below) | Watch daily numbers (below). Do not add features |
| 7 | **Mid-test survey** (the 8 questions) | Triage answers; list top 5 problems |
| 8 | Release 1.0.x with the top fixes | Tell testers what changed (Play wants evidence you acted on feedback) |
| 9 to 12 | Re-test the fixed things; battery guide improvements | Compare Crashlytics before/after |
| 13 | **Final survey** (same 8 questions) and a 10-minute call with 3 testers | Compare to day 7 |
| 14 | Test completes (14 continuous days of 12+ opted-in) | Write the go / no-go note; apply for production access |

Daily one-line asks: Day 4 "open Instagram Reels and swipe exactly 20; tell me the number in the app", Day 5 "turn the phone off
overnight and on; is the counter on?", Day 6 "send your friend a roast sticker", and so on. Keep each ask under two minutes.

## Count check (accuracy)
Each tester, once on day 0 and once on day 10: set the app's count to today, open one app (Instagram Reels, then YouTube
Shorts), swipe through **exactly 20** reels, and write what the app shows. Accuracy = shown / 20. Target: **18 to 22 on every
app and phone** (within 10 percent). Record the phone and app. A phone that always shows 0 needs the battery guide or a new
view id (`adb logcat -s ReelSurfaceDiag`, then `assets/surface_rules.json`). Facebook Reels and Snapchat Spotlight have no
verified view id yet, so expect to fix them in this test.

## What to measure
| Area | Metric | Where from | Target to go live |
|---|---|---|---|
| Stability | Crash-free users | Crashlytics | 99% or better |
| Stability | ANR rate | Play Console > Android vitals | under 0.47% |
| Activation | Testers who turned the counter on (disclosure Agree, service on) | `permission_granted` | 18 of 20 within day 1 |
| Disclosure | Testers who tapped Not now at least once, and why | Survey Q4 | understand the reasons |
| Retention | Opened the app on day 1, 7, 14 | Firebase Analytics / Play Console | D1 80%, D7 65%, D14 50% (friends; real users will be lower) |
| Core loop | Duels created and finished per pair | `duel_created`, `duel_finished` | every pair finishes at least 1 |
| Counting | Count check accuracy | Count check | within 10% on 90% of phone+app pairs |
| Counting | "Counter stopped by itself" reports | Survey Q5 + matrix B2, B3, B5 | none on must-devices |
| Locks | `lock_triggered` per active tester, and "did a lock ever fail to block" | Analytics + Survey Q6 | zero silent failures |
| Notifications | Unlock request delivery time | Matrix N1 to N5 (seconds) | median under 10 s, none over 60 s |
| Friend unlock | `unlock_requested` by `result` | Analytics | `failed` and `no_network` under 10% |
| Paywall | `paywall_viewed` -> `subscription_started` with license testers | Analytics + Play Console | the purchase works end to end on 3 phones, restore works |
| Privacy | Anyone who asked what data we take, anyone confused by a screen | Survey Q8 | all questions answered in the FAQ |
| Battery | Battery use of the app | Android Settings > Battery (testers screenshot on day 7) | under 2% per day |
| Deletion | 2 testers delete their account in the app | Manual | data gone in the Firebase console |

Only use analytics from testers who tapped Haan on the anonymous-data card; ask all 20 to tap Haan for the test and say
why. Crash and ANR data from Play Console covers everyone either way.

## Feedback form: 8 questions
Make it a Google Form, anonymous or with an optional nickname. Same form on day 7 and day 13.

1. **How easy was it to turn the reel counter on?** (1 = very hard, 5 = very easy) + "What was confusing?" (short text)
2. **Phone and Android version** (short text, for example "Redmi Note 12, MIUI 14, Android 13") and **how often the counter worked** (always / mostly / sometimes / rarely / never)
3. **Did the count match what you actually watched?** (much too low / a bit low / about right / a bit high / much too high) + "Which app?" (Instagram / YouTube / Facebook / Snapchat)
4. **The disclosure screen (the one before the permission).** Did you understand what the service does and does not do? (yes clearly / mostly / not really / I did not read it) + "What would you change?"
5. **Did the counter or a lock ever stop working by itself?** (no / once / a few times / often) + "When, and what were you doing?" (include battery saver, restart, overnight)
6. **Wait-10, Strict Lock, Bedtime and friend unlock: which did you use, and did each one work as you expected?** (matrix: used / did not use, worked / did not work, with a text box)
7. **Which would you pay for, if anything?** Pick up to 2: Unlimited duels / Squad Battle / Strict Lock / Detailed stats / Custom schedules / Brain skins / None of these. + "What price would feel fair per month?" (short number)
8. **What is the one thing we must fix before launch, and would you recommend the app to a friend?** (0 to 10, then short text) + "Anything about your data or privacy that worried you?" (short text)

Optional end: "Can we contact you for a 10-minute call?" (email field).

Read the answers into one sheet. Tag each with a number: S1 / S2 / S3 (see the matrix) or "idea". Fix every S1, and every S2
that two or more people mention.

## Tester messages (copy and send)
**Invite (Hinglish):**
"Mere app Doomscroll Duel ka beta test chal raha hai. 14 din tak install rakhna hai (beech mein uninstall mat karna, warna
test ruk jata hai). Link: [closed test link]. App 16+ ke liye hai. Aapse sirf ek cheez chahiye: app roz chalu rehne do aur
2 minute ke chhote kaam kar do jo main group mein bhejunga. Aur haan, shuru mein 'Anonymous data' wale card par 'Haan, bhejo'
dabana taaki bug mil sakein. Naam ya content kuch nahi jata. Shukriya!"

**Day-0 first 10 minutes:** install, read the disclosure, tap Agree, switch the service on, do the count check, add a friend,
send a duel.

## Rules for the test
- Test builds only through the Play closed track (so installs, updates and vitals are real).
- No new features during the 14 days, only fixes. Every fix goes out as a new build with a higher `VERSION_BUILD`.
- Keep a change log (date, what, why, who reported). Play's production application asks for it.
- Never ask testers for screenshots of messages, other apps' content or personal data. Ask for the app's own screens only.
- Anyone can quit at any time; deleting their account is in Settings.

## Go / no-go (decide on day 14)
**Go** to production at 10% staged rollout if: no open S1 on a must-device, crash-free users 99% or better, ANR under 0.47%,
count accuracy within 10% on most phones, unlock delivery median under 10 s, purchase and restore work, 2 account deletions
verified, and the public pages and Play forms are complete. **No-go** otherwise: fix, add 7 more days, keep testers opted in.

## Production access application: notes to reuse
- How testers were found: friends and classmates, invited by link and group.
- What was tested: install and update, accessibility disclosure and consent, counting accuracy on 5 phone makers, locks, friend
  unlock, notifications, subscription with license testers, account deletion.
- What was found and changed: (copy from the change log, at least 3 real items).
- Why the app is ready: the go criteria above, with the numbers.
