# Device test matrix: Samsung, Xiaomi, Realme, Oppo, Vivo, Android 10 to 15

Nothing in this file has been run yet. It is the plan for the real phones. Phone makers change their battery and
permission screens between versions, so the menu names below are the usual ones, not a promise.

## Why these tests
The app's value depends on four things the phone maker can break: the Accessibility service keeps running, the permission
stays on, notifications arrive on time, and the "Brain bachao" overlay appears. Standard Android does all four; the
makers' "battery savers" are where it fails.

## Phones to cover (pick the real ones your 20 testers own first)

Android version -> usual maker skin. Aim for at least one phone per row; rows marked **must** are the most common in India.

| Maker | Android 10 | Android 11 | Android 12 | Android 13 | Android 14 | Android 15 |
|---|---|---|---|---|---|---|
| **Samsung** (One UI) | One UI 2 | One UI 3 | One UI 4 | One UI 5 **must** | One UI 6 **must** | One UI 7 |
| **Xiaomi / Redmi / Poco** | MIUI 12 | MIUI 12.5 | MIUI 13 | MIUI 14 **must** | HyperOS 1 **must** | HyperOS 2 |
| **Realme** | Realme UI 1 | Realme UI 2 | Realme UI 3 | Realme UI 4 **must** | Realme UI 5 | Realme UI 6 |
| **Oppo** | ColorOS 7 | ColorOS 11 | ColorOS 12 | ColorOS 13 **must** | ColorOS 14 | ColorOS 15 |
| **Vivo / iQOO** | Funtouch 10 | Funtouch 11 | Funtouch 12 | Funtouch 13 **must** | Funtouch 14 | Funtouch 15 / OriginOS |

Minimum set if phones are scarce (6 devices): Samsung One UI 5 and One UI 6, Redmi MIUI 14 or HyperOS 1, Realme UI 4,
Oppo ColorOS 13, Vivo Funtouch 13. Add one Android 10 and one Android 15 device from any maker.

Each device is tested in two start states: **A = default settings, nothing changed** (what most users have) and
**B = the app's battery guide followed** (battery set to unrestricted, autostart on, app locked in recents).
The gap between A and B tells you what the battery guide must say for that maker.

## Maker settings to know (names vary)

| Maker | Where the killers live |
|---|---|
| Samsung | Settings > Battery and device care > Battery > Background usage limits: "Put unused apps to sleep", "Sleeping apps", "Deep sleeping apps", "Never sleeping apps". Also "Auto optimize daily" and Adaptive battery. Add the app to **Never sleeping apps** |
| Xiaomi / Redmi / Poco | Settings > Apps > Manage apps > (app) > **Autostart**; Battery saver > **No restrictions**; Security app > Permissions; "Lock" the app in Recents (pull down on the card); "Display pop-up windows while running in the background"; Notifications > Floating notifications |
| Realme | Settings > Battery > App battery management > (app) > **Allow background activity**, **Allow auto-launch**; Settings > Apps > Auto launch |
| Oppo | Settings > Battery > More battery settings > Optimize battery use; App management > (app) > **Allow auto-launch**, Allow background activity (Battery usage) |
| Vivo / iQOO | Settings > Battery > Background power consumption management > (app) > **Allow high background power consumption**; Settings > Apps > Autostart; i Manager > App manager > Autostart |

## Test cases

Each case has an id, the steps, and what must happen. Mark P (pass), F (fail) or N/A on the result sheet below.
Severity if it fails: **S1** the counter or a lock silently stops, **S2** it works but needs a workaround the guide
does not mention, **S3** cosmetic.

### Group 1: battery optimisation killing the service
| Id | Steps | Expected |
|---|---|---|
| B1 | Start A. Turn the counter on (disclosure, Agree, enable service). Open Instagram Reels, swipe 10. Open the app | Count is 10 |
| B2 | Lock the phone for 30 minutes (screen off). Unlock, open Reels, swipe 10 | Count is 20; service still on in Android Settings |
| B3 | Same as B2 for 4 hours (overnight), phone on the charger and then off the charger | Count is right; service still on. Note if the "counter on" notification vanished |
| B4 | Swipe the app away from Recents, then swipe 10 reels | Counts; service still on |
| B5 | Use the maker's "Clean up" / "Optimise" / "Boost" button or "Clear all" in Recents, then swipe 10 reels | Counts. If not: S1 for that maker, and the guide must name that button |
| B6 | Turn Battery saver / Power saving mode on, swipe 10 reels. Then Ultra power saving if it exists | Counts. Note any maker that disables accessibility in extreme saving |
| B7 | Reboot the phone. Do **not** open our app. Open Reels, swipe 10 | Counts after the reboot with no action from the user; the notification returns when the app is next opened (it cannot start from the background on Android 12+) |
| B8 | Force-stop the app from Android Settings > Apps. Check the Accessibility list | Android turns the service off after a force-stop; the app must show the "Counting band hai" banner when opened, and the fix button works |
| B9 | Follow state B (battery guide) and repeat B2, B3, B5 | All pass. Record which guide steps were actually needed |
| B10 | Let the phone sit untouched for 3 days with the app unopened, then swipe reels | Counts, or the banner shows after opening (record which) |

### Group 2: permission resets and reappearing prompts
| Id | Steps | Expected |
|---|---|---|
| P1 | Turn the service off in Android Settings > Accessibility, then on again | Banner appears within a second of off and disappears on on |
| P2 | Android 11+: simulate "auto-reset unused app permissions": `adb shell cmd app_hibernation set-state com.doomscrollduel.debug true` (command names differ a little between Android versions; if it is refused, use Settings > Apps > (app) > "Pause app activity if unused" and wait), then reboot | Accessibility is not auto-revoked by Android; notifications may be. The app notices (banner / Settings status chip) and re-asking works |
| P3 | Android 13+: deny notifications at the first prompt, then use the Settings "Fix" button | Opens the app notification settings (the system will not ask twice); chip turns green after turning it on |
| P4 | Android 14+: on first use, check the foreground service notification starts without a crash | Notification appears; no `ForegroundServiceStartNotAllowedException` in logcat |
| P5 | Maker "App permissions" manager (Xiaomi Security app, Vivo i Manager) resets the app's permissions after an update or "optimise" | After the reset the chips and banner show the real state |
| P6 | Restricted settings (apps installed from a file, Android 13+) greying out the Accessibility toggle | Only on sideloaded builds, not on Play installs. Note for internal testers: allow it in App info > menu > Allow restricted settings |
| P7 | Remove the disclosure consent (clear app data), re-enable the service directly in Android Settings | Counter stays at 0 and the banner asks for the disclosure; after Agree it counts |
| P8 | Update the app over the previous build (install the new APK/AAB on top) | Service stays enabled; Android may rebind it. Note if the maker turns it off after an update |

### Group 3: notification delivery (friend unlock and results)
Use the debug "send test push" or a real unlock request between two phones. Measure seconds from send to the notification appearing.

| Id | State of the receiving phone | Expected |
|---|---|---|
| N1 | App open | Notification (or in-app) within 10 s |
| N2 | App in the background, screen on | Within 10 s |
| N3 | Screen off, phone just locked | Within 10 s |
| N4 | App swiped away from Recents | Within 10 s |
| N5 | After the phone has been idle 30+ minutes (Doze): `adb shell dumpsys deviceidle force-idle` then send | A high-priority message wakes the phone; within about 30 s. Note makers that hold it until the screen is turned on |
| N6 | Battery saver on | Delivered; note delay |
| N7 | Do Not Disturb on | Silent per DND rules, but visible in the shade |
| N8 | Notifications off for the app (Android 13+ denied) | The app shows the chip as off and the Fix button works; no crash |
| N9 | Notification channel importance set to Low by the user or maker | Note; the guide should say where to change it |
| N10 | Tap Approve and Deny buttons from the notification, screen locked and unlocked | Approve/Deny reaches the server and the requester's phone lifts the lock (15 min) or stays locked |
| N11 | Two unlock requests arrive close together | Two notifications, each with its own buttons, not merged |
| N12 | Network off when the notification arrives, then on | Answer is sent when back online; late approval gives a shorter pass (pass is 15 min minus delay) |
Record the delay in seconds and which maker feature (autostart, battery, "background freeze") changed it.

### Group 4: the overlay and Back/Home
| Id | Steps | Expected |
|---|---|---|
| O1 | Switch on Wait-10, open Instagram Reels | Within about a second a card with a 10 second countdown and the breathing animation covers the reel; the video is silent/paused |
| O2 | Press RAHNE DO | Returns to the previous screen / Home; the card is gone |
| O3 | Wait 10 s, press the continue button | Card gone, reels play. Leaving and returning within 3 s keeps the session; longer starts a new gate |
| O4 | Reach the limit with Strict Lock on (set the limit low, 10) | The lock starts right after the reel that reaches the limit; "Brain bachao" shows with the time left; Back, then Home if still on the reel |
| O5 | Bedtime mode: set a window that includes now. Open Reels | Blocked with the bedtime text |
| O6 | Overlay over: YouTube Shorts, Facebook Reels, Snapchat Spotlight | Same behaviour (record apps whose reel screen is not detected: their view id is unverified) |
| O7 | 3-button navigation and gesture navigation | Overlay is not covered by the system bars; the buttons are tappable |
| O8 | Portrait and landscape, split screen, picture-in-picture (YouTube PiP) | Overlay covers the right area; nothing crashes |
| O9 | Font size 200% and display size large | Texts readable, buttons reachable (scrolls if needed) |
| O10 | Dark mode on/off, high contrast text | Readable |
| O11 | TalkBack on | Overlay is announced; buttons are reachable and named |
| O12 | Makers' floating-window / game / focus modes (Samsung Game Launcher, Xiaomi Game Turbo, Realme/Oppo Game Space) | Overlay still appears over the reel app |
| O13 | Reduced motion (Remove animations) | Breathing animation is static, countdown still works |
| O14 | Wait for the lock to end while the overlay is showing | Overlay closes by itself |
| O15 | Remove the overlay's permission? There is none: it is an Accessibility overlay, not "Display over other apps" | Confirm the app does NOT ask for "Display over other apps" and that turning that setting off for the app changes nothing |

### Group 5: time and clock edge cases (quick, on 2 devices)
| Id | Steps | Expected |
|---|---|---|
| T1 | During a 3-hour lock set the phone clock +1 day | Lock time left does not shrink |
| T2 | During a lock set the clock back 2 hours | Lock time left does not grow |
| T3 | Change the time zone during a lock and during a bedtime window | Lock unaffected; the bedtime window follows the new local time |
| T4 | Reboot during a lock | Lock continues with about the right time left; Strict Lock still blocks |
| T5 | Airplane mode all day | Counting, locks and Wait-10 all work; friend unlock says "net nahi hai" and does not use a request |

## Result sheet (copy into a spreadsheet)
One row per device per start state.

| Phone model | Maker UI | Android | Start state (A/B) | B1 | B2 | B3 | B4 | B5 | B6 | B7 | B8 | B9 | B10 | P1 | P2 | P3 | P4 | P5 | P7 | P8 | N1 | N2 | N3 | N4 | N5 | N6 | N8 | N10 | N12 | O1 | O2 | O3 | O4 | O5 | O6 | O7 | O8 | O9 | O11 | O12 | T1 | T4 | Notes, delays, screenshots |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

Pass criteria for launch: no S1 on a **must** device; every S1 on another device has a line in the in-app battery guide for
that maker; B1 to B5 and O1 to O5 pass on at least 5 different makers/versions.

## ADB cheat sheet (debug build id `com.doomscrollduel.debug`)
```
adb shell settings get secure enabled_accessibility_services      # is the service on?
adb shell dumpsys accessibility | grep -i doomscroll               # is it bound?
adb shell dumpsys deviceidle force-idle                            # enter Doze now
adb shell dumpsys deviceidle unforce                               # leave Doze
adb shell am set-standby-bucket com.doomscrollduel.debug rare      # app standby bucket
adb shell dumpsys battery unplug                                   # pretend the charger is out
adb shell cmd app_hibernation set-state com.doomscrollduel.debug true   # Android 11+ hibernation
adb shell am force-stop com.doomscrollduel.debug                   # force-stop
adb logcat -s ReelSurfaceDiag                                       # debug builds: class names and layout ids of scrolls
adb shell dumpsys notification --noredact | grep -A5 doomscroll   # notification state
```
For the 3-day idle test (B10) leave the phone alone and run the first two commands at the end.

## When something fails
Add a line to the in-app battery guide for that maker (`tracking/health/BatteryGuide.kt`), re-test with start state B, and
log it. If a maker kills the Accessibility service whatever the user does, say so honestly on the Play listing and in the
FAQ instead of promising counting that cannot be kept.
