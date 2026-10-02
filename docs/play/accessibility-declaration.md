# Play Console: AccessibilityService API declaration

Where: Play Console > Policy and programs > App content > **Accessibility API / Permissions declaration**.
The form's questions change from time to time; the answers below are written in the shape Google asks for (what the
core feature is, what the API is used for, why nothing else works, what data is touched, a video). Copy each answer
into the matching box.

> Honest risk note. Google reviews every non-accessibility use of this API by hand, and this is the most common reason
> a digital-wellbeing app is rejected. Approval is not guaranteed. What improves your odds: the in-app disclosure (built),
> doing nothing until the person agrees (built), a service that provably reads nothing (guarded by a script), a clear
> video, and answers that match the app exactly. If Google rejects it, the fallback is a version without reel counting
> that uses only Usage Access (it can time app use but cannot count reels or block a single screen).

## 1. Is your app an accessibility tool?
**No.** The app is not designed to help people with disabilities. (The service is declared with
`android:isAccessibilityTool="false"` in `res/xml/reel_accessibility_service.xml`.)

## 2. Core functionality that needs the API
```
Doomscroll Duel is a friendly challenge game for people who want to watch fewer short videos. Its core feature is a
reel counter: it counts how many Reels, YouTube Shorts, Facebook Reels and Snapchat Spotlight videos the user swipes
through each day, so friends can compete for who watches fewer. When the user turns on a lock (Strict Lock, Bedtime
mode, Focus hours or the Wait-10 gate), the app also stops the user from opening those short-video screens by showing a
"Brain bachao" screen and pressing Back or Home. Without the AccessibilityService API the app cannot count swipes inside
other apps or return the user from a short-video screen, so the core feature would not exist.
```

## 3. How the API is used
```
- Event types: TYPE_VIEW_SCROLLED (a swipe), TYPE_WINDOW_CONTENT_CHANGED (the swipe may have changed the page) and
  TYPE_WINDOW_STATE_CHANGED (a tracked app came to the front).
- The service is limited in its XML configuration to four packages: com.instagram.android, com.google.android.youtube,
  com.facebook.katana and com.snapchat.android. Events from any other app are never delivered.
- From a scroll event we read only the app package name, the event time, and the scrolled view's class name and layout
  resource id (for example "reel_recycler"), to tell a reels pager from the normal feed. We never call getText,
  getContentDescription, or walk the screen.
- canRetrieveWindowContent is true ONLY so the blocker can ask "is a view with this layout id on screen?" (one yes/no
  question, via findAccessibilityNodeInfosByViewId, in a single class). The answer is a yes/no. Nothing on screen is
  read, stored or sent.
- performGlobalAction(BACK or HOME) is used only when a lock the user switched on is active, to leave a reels screen.
- An overlay (TYPE_ACCESSIBILITY_OVERLAY) shows the "Brain bachao" screen. No tapping, gestures, typing or touch
  exploration (canPerformGestures is false).
- Nothing is processed until the user taps "Agree" on a full-screen in-app disclosure; "Not now" leaves everything off.
```

## 4. Why can't another API do this?
```
UsageStatsManager only tells us how long an app was open, not how many short videos were swiped, and it cannot return
the user from a single screen inside an app. Notification and media-session APIs do not report swipes. The apps offer
no public API for view counts. The Accessibility service is the only Android API that reports scroll events of another
app's screen to the user's own app with the user's permission.
```

## 5. What user data is accessed, and what happens to it
```
Accessed: which of the four apps is in the foreground, that a scroll happened, the scrolled view's class name and layout
id, and whether a reels view id is on screen. Not accessed: text, messages, passwords, names, captions, comments,
what the user watches, any other app, screenshots or screen recordings.
Stored: a single number per app per day (the reel count) on the device. Uploaded only for a challenge the user joins,
and visible only to the other people in that challenge. Not sold, not shared with advertisers, not used for ads.
Users can switch the service off at any time in Android Settings > Accessibility, and can delete their account and all
server data inside the app.
```

## 6. Prominent disclosure and consent
```
Before the system permission is requested, the app shows a full-screen disclosure (Hinglish, with an English summary)
that says what the service does, what it does not do, and that data is not shared. The user must tap "Agree". "Not
now" and Back do nothing and the permission is not requested. Without Agree, the service ignores every event even if
it has been switched on in Android Settings. The agreement is stored with a version number; if the text changes, users
are asked again. The text is in app/src/main/res/values/disclosure_strings.xml.
```

## 7. Does the service ever act without the user's request?
```
Counting runs after the user's agreement. Blocking happens only when the user has switched on a lock themselves. A
Strict Lock cannot be switched off inside the app while it runs (it is a commitment the user made), but the user can
always disable the Accessibility service in Android Settings or uninstall the app, and the app says so.
```

## 8. Video script (what to record)

**Format:** one take, 60 to 120 seconds, portrait, screen recording of a real device (or emulator) with a voice-over in
English, uploaded to YouTube as **Unlisted**, link pasted in the form. Phone language: English or Hindi. No personal
messages, notifications or accounts visible (use a test account; turn on Do Not Disturb). Show the touches (Developer
options > Show taps).

| Time | Show | Say |
|---|---|---|
| 0:00 | Home screen, open Doomscroll Duel (first launch, no permission yet) | "This is Doomscroll Duel, a game where friends try to watch fewer reels. It counts reels in Instagram, YouTube, Facebook and Snapchat." |
| 0:10 | Tap the banner "Pehle ek zaroori jaankari padho" | "Before anything is requested, the app shows this disclosure." |
| 0:15 | Scroll slowly through the disclosure: what it does, what it does not do, data, English summary | "It explains what the service does, what it does not do, that it does not read messages, passwords or what I watch, and that data is not shared or used for ads." |
| 0:35 | Tap **NOT NOW** once; show you return to Home with counting still off; then reopen and tap **AGREE** | "If I tap Not now, nothing is requested and counting stays off. I'll tap Agree." |
| 0:45 | Android Accessibility settings open; find Doomscroll Duel; open its page; switch it on; read the system prompt | "Only now does Android's permission screen open. I switch the service on." |
| 0:55 | Open Instagram, go to Reels, swipe 5 times | "In Instagram Reels I swipe five times." |
| 1:05 | Return to Doomscroll Duel, show count = 5, with the per-app number | "The app shows five reels counted. Only this number is stored. No titles, accounts or content." |
| 1:15 | Settings: switch on Wait-10 (or Strict Lock with a low limit); open Instagram Reels | "I switch on a lock myself. When I open Reels, the app shows this screen and presses Back." |
| 1:30 | Show the "Brain bachao" screen and being returned | "The service pressed Back for me. It did not read the video." |
| 1:40 | Android Settings > Accessibility > Doomscroll Duel > switch off; show the app banner "Counting band hai" | "I can switch the service off at any time in Android Settings." |
| 1:50 | Settings > Account delete karo screen | "I can also delete my account and all server data inside the app." |

Keep the English summary on screen long enough to read. Do not skip the Not now step: reviewers look for it.

## 9. Related declarations to fill at the same time
- **Foreground service type "special use"**: subtype text is in the manifest (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`: keeps the
  on-device reel counter running and shows today's count in a quiet notification). Explain in Play Console > App content >
  Foreground service permissions, with a short video of the notification.
- **POST_NOTIFICATIONS**: friend-unlock requests and the counter notification. Ask at a moment the user understands (the
  Settings "Fix" button does this).
- Nothing else sensitive is requested for the accessibility feature: no SMS, call log, location, SYSTEM_ALERT_WINDOW, QUERY_ALL_PACKAGES or
  REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (`scripts/check-play-readiness.sh` fails if one appears).
