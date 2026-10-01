# What the reel counter reads, stores and uploads

Use this for the Google Play AccessibilityService declaration, the in-app disclosure and the privacy policy.

## Why an AccessibilityService
It is the only way to notice that a short video was swiped to inside another app. It is used for that and
nothing else.

## What it can receive
- Only events from four packages: Instagram, YouTube, Facebook, Snapchat. The list is in
  `res/xml/reel_accessibility_service.xml` (`android:packageNames`), so the system never sends events from
  any other app.
- Only two event types: scroll and window content changed.
- It cannot tap, swipe, type or perform gestures (`canPerformGestures="false"`).

## What the code reads from an event
| field | used for |
|---|---|
| package name | which app |
| event type, event time | the swipe logic and the 600 ms spacing |
| class name and view resource id of the scrolled view (scroll events only) | telling the Shorts/Reels pager from the normal feed |

The view resource id is a layout identifier such as `reel_recycler`. It is not text the user sees.

## What the code never does
- Never calls `getText`, `getContentDescription`, or walks parents or children of the screen.
- Never reads usernames, captions, comments, search text, messages or video.
- Never logs identifiers in release builds. Debug builds print class name and view id to logcat so
  testers can find the right ids (`adb logcat -s ReelSurfaceDiag`). Nothing is saved.

## What is stored on the phone
One integer per app per day (see `data-model.md`). No content.

## What is uploaded
Nothing from the counter by itself. When the user is in an active duel, only that day's total and per-app
totals are uploaded for the duel, and only the opponent in that duel can read them, only while it is active.

## Disclosure text the user must see before enabling
The text in `a11y_service_description` must also be shown in a full-screen prominent disclosure with an
explicit "Haan, chalu karo" / "Nahi" choice before opening Accessibility settings (onboarding milestone).

## Blocking engine additions
The accessibility service now also: listens to `typeWindowStateChanged` (package name only), asks `ReelScreenProbe`
whether a known layout view id is on screen (a yes/no, nothing read), and performs the global Back and Home actions
when a lock/gate is active. Nothing new is stored or uploaded about what the user watches. The unlock flow uploads
only: requester uid, friend uid, status and timestamps.
