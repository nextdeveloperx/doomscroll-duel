# Play Console: Data safety form (answers)

Written from what the app does today (`docs/privacy-data-flow.md`, `functions/src/deletion.ts` registry). Re-check every
answer when you add a feature or an SDK (analytics, crash reporting, ads would all change it). The form must match the
Privacy Policy and the app's behaviour exactly; a mismatch is a common rejection reason.

## Section 1: data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is any collected data optional for users? | **Yes**: analytics, crash reports, dare proof photo or video |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS/TLS to Firebase and Google Play) |
| Do you provide a way for users to request that their data is deleted? | **Yes** |
| Delete account URL | `{{SITE_URL}}/delete-account` |
| Can users request that some data is deleted without deleting the account? | Optional. Today: **No** (dare proofs auto-delete after 7 days). Say "No" unless you build it. |

## Section 2: data types

For each type: Collected = sent off the device to us. Shared = transferred to a third party (service providers acting for
us, such as Google Firebase, are **not** "sharing" under Google's definition; other players seeing your own challenge
results is a user-directed feature, not a transfer to a third party).

| Category | Data type | Collected | Shared | Optional? | Why collected | Notes |
|---|---|---|---|---|---|---|
| Personal info | Name | Yes | No | Required | App functionality, Account management | Display name you choose |
| Personal info | Email address | Yes | No | Required if you sign in with Google | Account management | Sign-in |
| Personal info | Phone number | Yes | No | Required if you sign in by phone | Account management | Sign-in. Declare only if phone sign-in ships |
| Personal info | User IDs | Yes | No | Required | App functionality, Account management | Account ID, username |
| Financial info | Purchase history | Yes | No | Required for Pro | App functionality | Google Play purchase token, plan, status, end date. **No** card or UPI data reaches us, so do not tick "User payment info" |
| Photos and videos | Photos / Videos | Yes | No | **Optional** | App functionality | Dare proof, private between two players, deleted after 7 days. Declare only if the dare proof upload ships |
| App activity | App interactions | Yes | No | Required for challenges | App functionality | Daily reel counts (total and per app: Instagram, YouTube, Facebook, Snapchat) uploaded only for challenges you join; duel history |
| Device or other IDs | Device or other IDs | Yes | No | Notification token required; the Firebase install ID is **optional** (only if the person allows anonymous data) | App functionality (token); Analytics (install ID) | Firebase Cloud Messaging token and the Firebase installation ID. **Not** the advertising ID: the AD_ID permission is removed |
| App activity | App interactions (analytics) | Yes, **optional** | No | Optional (off until the person says yes) | Analytics | Nine anonymous events with bucketed values (`docs/analytics-events.md`). No names, ids, exact counts |
| App info and performance | Crash logs and Diagnostics | Yes, **optional** | No | Optional (off until the person says yes) | Analytics, App functionality | Crashlytics crash reports; exception messages are stripped; device model and OS version are attached by Firebase |

Not collected: Location, Contacts, Messages, Audio, Files and docs, Calendar, Health and fitness, Web browsing,
Installed apps, Advertising ID. Analytics and crash reports are in the table above and are collected only after the person says yes.

For each collected type, answer **"Is this data processed ephemerally?" = No**. Purposes: App functionality and Account management for account data; Analytics for the optional rows only. **Never** tick Advertising or marketing, Personalization, Fraud
prevention (unless you add it), or Developer communications (unless you send newsletters).

## Section 3: security practices
- Data encrypted in transit: **Yes**
- Users can request data deletion: **Yes**
- Committed to Google Play Families Policy: **No** (the app is not for children)
- Independent security review: **No**

## About data from the Accessibility service
Nothing the service reads leaves the phone except the reel count. Declare it as "App interactions". Do not declare
"Messages", "Web browsing", "Installed apps" or "Photos": the service never reads them (guarded by
`scripts/check-service-privacy.sh`).

## Optional explanation to paste (if the form gives a free-text field)
"The app counts how many short videos you swipe in four named apps using Android's Accessibility service. The service
reads no text or content. Only the daily count is stored, on your phone, and uploaded only for a challenge you join.
Data is not sold, not shared with advertisers, and not used for ads. You can delete your account and all server data in
the app."
