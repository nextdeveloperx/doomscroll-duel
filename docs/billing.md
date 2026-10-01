# Pro subscription and billing

## Plans
| | Free | Pro |
|---|---|---|
| Reel counting | yes | yes |
| 1v1 duels | 3 started per day | unlimited |
| Night Pact, Forfeit Dare, Wait-10 gate, Bedtime mode | yes | yes |
| Squad Battle | no | yes |
| Strict Lock | no | yes |
| Custom block schedules (Focus hours) | no | yes |
| Detailed analytics (per app, per hour) | no | yes |
| Extra brain skins | no | yes |

Pro is one Play subscription product, `doomscroll_pro`, with two base plans: `monthly` and `yearly`.
Prices are never written in the app: they come from Play in the user's currency, and the "x% bachat" badge is
computed from those prices (and hidden when it cannot be said honestly).

## How a purchase becomes Pro (the app never trusts itself)
```
paywall -> BillingManager.buy()  -> Play purchase sheet (obfuscatedAccountId = Firebase uid)
        -> purchase token        -> verifyPurchase (Cloud Function)
                                     1. asks Google: purchases.subscriptionsv2.get
                                     2. refuses if productId is not ours, or the account id is not the caller's
                                     3. links token -> uid (a token can belong to one account only)
                                     4. writes entitlements/{uid}   (admin SDK; clients cannot write it)
                                     5. acknowledges the purchase (Google refunds unacknowledged ones after 3 days)
        -> Firestore listener    -> EntitlementService -> ProView -> screens
```
`BillingManager` never grants anything. A hacked app can only send a token, and a token that is not a live Pro
subscription bought by that account changes nothing.

Server entitlement document (`entitlements/{uid}`): `status`, `plan`, `accessUntilMs`, `autoRenewing`, `verifiedAtMs`.

## Subscription states
| Play state | Our status | Pro? | What the user sees |
|---|---|---|---|
| ACTIVE, renewing | ACTIVE | yes | "Tum Pro ho, next renewal date" |
| ACTIVE / CANCELED, renewal off | CANCELED | yes, until the paid time ends | "Pro <date> ko khatam hoga" + resubscribe |
| IN_GRACE_PERIOD | GRACE | yes | "Payment fail hui, <date> tak theek karo" + fix payment |
| ON_HOLD | ON_HOLD | no | "Payment theek karo, Pro wapas aa jayega" |
| PAUSED | PAUSED | no | "Subscription pause hai" |
| PENDING (UPI, cash, carrier) | PENDING | no | "Payment confirm ho raha hai", no second purchase needed |
| EXPIRED, refunded, revoked | EXPIRED | no | "Pro khatam ho gaya, wapas aana hai?" |
| anything unknown | NONE | no | normal paywall |

Pro features checked on the phone use `Entitlements.view(...)`. Things that must be secure are checked on the server
(`entitlementGate.ts`: free duel quota, squad creation) because the phone's checks only change what a screen shows.

## Keeping Pro right
- **Real-time notifications**: Play publishes renew / cancel / grace / hold / revoke to Pub/Sub topic `play-rtdn`;
  `playNotifications` re-reads the token from Google and rewrites the entitlement. The message content is never trusted.
- **Safety net**: `refreshStaleEntitlements` runs every 6 hours; entitlements whose paid time is ending are re-checked,
  and ones that cannot be confirmed after their end are set to EXPIRED.
- **Restore**: the paywall's "Purchase restore karo" button, and a quiet check at most every 6 hours when the app opens,
  read the subscription Google has on this account and send it through `verifyPurchase`. This covers reinstalls,
  a new phone and a purchase made while the app was closed.
- **Offline**: the last entitlement is cached on the phone. Pro keeps working until `accessUntilMs`; a renewing plan gets
  3 extra days of slack so a renewal the phone has not heard about does not switch Pro off. A phone clock set more
  than a day behind the server's last check fails closed ("Phone ka time galat hai") so turning the clock back cannot
  keep Pro for ever.
- **Pro ends**: nothing a person already set up is taken away. A running Strict Lock and focus schedule keep working and
  can be switched off or trimmed (subject to the commitment rule); only switching ON or adding ranges needs Pro.
  Pro skins fall back to the default and come back with Pro.

## Setup you must do (nothing here has been done)
1. Play Console: create subscription `doomscroll_pro`, base plans `monthly` and `yearly` (no promo offers; the app uses
   the plain base plan), set a grace period and account hold under Subscriptions > Settings.
2. Play Console > Monetization setup: add a Pub/Sub topic `play-rtdn` (Cloud project of the Firebase project) and the
   Real-time developer notifications; grant `google-play-developer-notifications@system.gserviceaccount.com` publisher.
3. Play Console > Users and permissions: invite the Cloud Functions service account
   (`<project>@appspot.gserviceaccount.com` or the one you set) with "View financial data" and
   "Manage orders and subscriptions". This can take a day to start working.
4. Set the function parameter `PLAY_PACKAGE_NAME` (default `com.doomscrollduel`).
5. `cd functions && npm install && npm test && firebase deploy --only functions,firestore:rules`.
6. Add license testers in Play Console and run the test plan in `docs/billing-test-plan.md`.

## Open questions (verify with a real test purchase before launch)
- Whether `lineItems[].expiryTime` during GRACE is the extended end of access. The server fails closed (a grace whose
  end is already past becomes ON_HOLD), so the worst case is a paying user in grace losing Pro early until the next
  notification; check this with a license tester whose test card is set to fail.
- Field names of the `subscriptionsv2` response were written from the API reference, not from a live response.
- `users/{uid}.timeZone` is not writable by clients yet (rules only allow displayName and avatarColor), so the daily
  check-in day uses India time until a profile function stores the zone.
