# Manual test checklist: reel counter

Run on a **debug build** (so the Home screen and logcat can be checked) and repeat the "Basics" block on
a **release build** before shipping. Use a real phone, not an emulator: the four apps behave differently
there. Record the phone model, Android version, and each app's version in the table at the end.

How to read the count: Home screen (big number plus "Kahan kitni reels") and the quiet notification
("Aaj: N reels"). Reset between tests by noting the number before and after.

Counting rule being tested: **one reel per swipe to a new short video, and two counted reels are never
closer than 600 ms.**

---

## 0. First: find and confirm the view ids (do this per app, per app version)

The counter only counts a scroll whose view id is listed in `app/src/main/assets/surface_rules.json`.
Those ids are shipped **unverified**: Instagram and YouTube have best-guess ids, **Facebook and Snapchat
have none yet and will count nothing until you add them**.

1. Install the debug build, turn the counter on (Settings > Accessibility > Doomscroll Duel).
2. `adb logcat -c && adb logcat -s ReelSurfaceDiag`
3. In the app, swipe through Reels/Shorts/Spotlight 5 times. You will see lines like
   `scroll pkg=... class=androidx.recyclerview.widget.RecyclerView viewId=com.google.android.youtube:id/reel_recycler`.
4. The id that appears on **every** swipe inside the short-video player (and not while scrolling the normal
   feed) is the pager id. Put the part after `:id/` into `viewIds` for that package.
5. Scroll the normal feed, comments, stories and search. Confirm that id does **not** appear there.
   If it does, add the printed `class=` to `classNames` to narrow the match.
6. Obfuscated ids (Facebook and Snapchat often look like `id/0_resource_name_obfuscated` or a hex number):
   use them as printed, and expect them to change when the app updates. Re-check after each app update.
7. Set `"verified": true` for the app only after sections 1 and 2 below pass for it.

---

## 1. Basics (every app)

| # | Do | Expected | Pass |
|---|---|---|---|
| B1 | Open the short-video surface, swipe to the next video 10 times, about 2 s apart | exactly +10 | [ ] |
| B2 | 10 swipes about 0.8 s apart | +10 | [ ] |
| B3 | 10 swipes about 0.3 s apart (very fast) | fewer than 10, never more than 10; no two counts closer than 0.6 s | [ ] |
| B4 | Drag the video up halfway and let go (page does not change), 5 times | +0 | [ ] |
| B5 | Swipe forward 5, swipe back 5 | +10 (one per swipe, either direction) | [ ] |
| B6 | Pause, resume, tap, double-tap to like, open and close comments, open share sheet | +0 | [ ] |
| B7 | Scroll the comments list up and down | +0 | [ ] |
| B8 | Leave the surface and scroll the normal home feed for about 30 items | +0 | [ ] |
| B9 | Let a video loop for 60 s with no touch | +0 (auto-advance is not a swipe) | [ ] |
| B10 | Press Home, return to the app, continue swiping 5 | +5, no double count on return | [ ] |
| B11 | Switch to another app and back mid-session (a different one of the four) | counts go to the right app each time | [ ] |
| B12 | Swipe 20, then force-stop **Doomscroll Duel** from Settings > Apps, reopen it | count still shows the 20 (stored in Room) | [ ] |
| B13 | Swipe 20, reboot the phone, unlock | count still there; notification back after reboot | [ ] |
| B14 | Open an app that is **not** one of the four and scroll a lot | +0 and no events (check `adb shell dumpsys accessibility` lists only the four packages) | [ ] |

---

## 2. Per app

### Instagram (`com.instagram.android`), surface: Reels

| # | Do | Expected | Pass |
|---|---|---|---|
| IG1 | Reels tab, swipe 10 | +10 | [ ] |
| IG2 | Home feed: tap a reel in the feed so the full-screen Reels viewer opens, swipe 5 | +5 | [ ] |
| IG3 | Profile > Reels tab, open one, swipe 5 | +5 | [ ] |
| IG4 | Open a reel sent in a DM, swipe 3 | +3 or +0, note which (DM viewer may use another id) | [ ] |
| IG5 | Home feed scroll 30 posts incl. feed videos | +0 | [ ] |
| IG6 | Stories: tap through 10 stories | +0 | [ ] |
| IG7 | Explore grid scroll | +0 | [ ] |
| IG8 | Instagram "Reels" with auto-scroll on, if offered | +0 | [ ] |

### YouTube (`com.google.android.youtube`), surface: Shorts only

| # | Do | Expected | Pass |
|---|---|---|---|
| YT1 | Shorts tab, swipe 10 | +10 | [ ] |
| YT2 | Home feed Shorts shelf: tap one, swipe 5 inside the Shorts player | +5 | [ ] |
| YT3 | Channel > Shorts, open one, swipe 5 | +5 | [ ] |
| YT4 | Home feed scroll 30 items | +0 | [ ] |
| YT5 | Play a normal (landscape or portrait) video, scroll the related list and comments | +0 | [ ] |
| YT6 | Swipe the Shorts shelf sideways on the home feed | +0 | [ ] |
| YT7 | Shorts "auto-advance" if enabled | +0 | [ ] |

### Facebook (`com.facebook.katana`), surface: Reels only

| # | Do | Expected | Pass |
|---|---|---|---|
| FB0 | Section 0 done: ids found and added (counts are 0 until then) | ids recorded | [ ] |
| FB1 | Reels (Video tab > Reels, or the Reels shortcut), swipe 10 | +10 | [ ] |
| FB2 | Tap a Reel in the News Feed so it opens full screen, swipe 5 | +5 | [ ] |
| FB3 | News Feed scroll 30 posts incl. autoplay video | +0 | [ ] |
| FB4 | Watch tab regular videos, scroll the list | +0 | [ ] |
| FB5 | Stories row, tap through 5 | +0 | [ ] |
| FB6 | Marketplace and Groups scroll | +0 | [ ] |

### Snapchat (`com.snapchat.android`), surface: Spotlight only

| # | Do | Expected | Pass |
|---|---|---|---|
| SC0 | Section 0 done: ids found and added (counts are 0 until then) | ids recorded | [ ] |
| SC1 | Spotlight tab, swipe 10 | +10 | [ ] |
| SC2 | Chat list and a conversation, scroll and send nothing | +0 | [ ] |
| SC3 | Camera screen, swipe between tabs | +0 | [ ] |
| SC4 | Stories and Discover, tap through 10 | +0 | [ ] |
| SC5 | Map tab pan and zoom | +0 | [ ] |

---

## 3. Stability

| # | Do | Expected | Pass |
|---|---|---|---|
| S1 | Turn the accessibility service **off** in system settings while the app is open | red "Counting band hai!" banner appears on Home within 2 s | [ ] |
| S2 | Tap "CHALU KARO" on the banner | opens the system Accessibility screen | [ ] |
| S3 | Turn it back on and return to the app | banner disappears without restarting the app | [ ] |
| S4 | With the service off, swipe 10 reels in Instagram | count does not move (and banner shows) | [ ] |
| S5 | Turn it off while the app is in the background, then open the app | banner is already there | [ ] |
| S6 | Check the notification shade | one quiet notification "Reel counter chalu hai / Aaj: N reels", no sound, hidden on the lock screen | [ ] |
| S7 | Notification number follows the Home number | matches within 1 s | [ ] |
| S8 | Swipe the app away from recents, keep swiping in Instagram for 2 minutes | counts keep rising; reopen the app and it matches | [ ] |
| S9 | Lock the screen for 30 min, unlock, swipe 10 | +10 | [ ] |
| S10 | 60 minute soak: scroll Reels for 60 minutes in 4 bursts with 5 minute gaps | final total within 5 percent of a manual tally | [ ] |
| S11 | Battery: leave "Battery optimization" restricted | orange "Battery saver counting rok sakta hai" banner; tapping opens the guide | [ ] |
| S12 | Follow the guide, then return to the app | banner goes away; guide shows "Battery theek hai" | [ ] |
| S13 | Battery guide shows the right steps for the phone (see OEM table) | steps match the maker | [ ] |
| S14 | "AUTOSTART SETTINGS KHOLO" | opens the maker's screen, or the app's own settings page if the maker's is missing; never crashes | [ ] |
| S15 | Android 13+: deny the notification permission | counting still works (the service still runs); the notification is just hidden | [ ] |

### OEM table (do S11 to S14 on each)

| Maker | Phone / ROM / version | Guide steps correct | Autostart screen opened | Counter survived overnight | Notes |
|---|---|---|---|---|---|
| Xiaomi / Redmi / Poco | | [ ] | [ ] | [ ] | |
| Realme | | [ ] | [ ] | [ ] | |
| Oppo | | [ ] | [ ] | [ ] | |
| Vivo / iQOO | | [ ] | [ ] | [ ] | |
| Samsung | | [ ] | [ ] | [ ] | |
| Other (Pixel / Motorola) | | [ ] | n/a | [ ] | |

The OEM screen names in `SystemIntents.kt` are widely used but private to each maker and change between
software versions. A miss is expected on some phones; the fallback is the app's settings page.

---

## 4. Midnight and time changes

Use a debug build. Turn off automatic date and time in system settings first.

| # | Do | Expected | Pass |
|---|---|---|---|
| M1 | Set the time to 23:58, swipe 5 in Instagram (Home shows 5) | 5 | [ ] |
| M2 | Wait for 00:00 with the app open on Home | number drops to 0 by itself within 1 s of midnight | [ ] |
| M3 | Swipe 2 | Home shows 2 | [ ] |
| M4 | `adb shell run-as com.doomscrollduel sqlite3 databases/doomscroll_duel.db "select * from reel_counts order by date"` | yesterday row shows 5, today shows 2 | [ ] |
| M5 | Set 23:59:50, keep swiping across midnight with the app **closed** | the swipes before 00:00 are on yesterday's row, after on today's | [ ] |
| M6 | Change the time zone from India to UTC at 02:00 IST and back | "today" re-evaluates; no negative or duplicated counts | [ ] |
| M7 | Streak: with yesterday and the day before under the limit (default 100), Home streak chip | 3 including today | [ ] |
| M8 | Push today's total to the limit (100) | streak chip drops to 0 immediately; brain turns zombie | [ ] |
| M9 | Phone off for a whole day, then on | that day has no row; streak ends there (by design) | [ ] |

---

## 5. Brain and numbers

| # | Do | Expected | Pass |
|---|---|---|---|
| N1 | Default limit 100: at 40 reels | HAPPY, HP 60 | [ ] |
| N2 | At 41 reels | FRIED, HP 59 | [ ] |
| N3 | At 99 reels | FRIED, HP 1 | [ ] |
| N4 | At 100 reels | ZOMBIE, HP 0 | [ ] |
| N5 | Sum of the four rows in "Kahan kitni reels" equals the big number | equal | [ ] |

---

## 6. Privacy audit (before every release)

| # | Do | Expected | Pass |
|---|---|---|---|
| P1 | `scripts/check-service-privacy.sh` | prints PRIVACY GUARD PASSED | [ ] |
| P2 | `adb shell dumpsys accessibility` | our service lists exactly the four packages | [ ] |
| P3 | Release build, `adb logcat` while swiping | no class names, view ids or package-per-swipe lines from our app | [ ] |
| P4 | `adb shell run-as` (debug) and inspect `databases/` and `shared_prefs/` | only counts and the daily limit; no text | [ ] |
| P5 | Network capture (mitmproxy) while swiping with no duel | nothing sent because of swipes | [ ] |
| P6 | In an active duel | only the day total and per-app totals are sent, to the duel's counts document | [ ] |

---

## Results log

| Date | Phone and Android | App versions (IG / YT / FB / SC) | Sections run | Failures and notes |
|---|---|---|---|---|
| | | | | |
