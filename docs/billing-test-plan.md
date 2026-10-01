# Billing test plan

Automated and passing: `EntitlementTest`, `EntitlementServiceTest`, `SettingGateTest`, `FreeLimitsTest`,
`PlanPricingTest`, `CoinWalletTest`, `CoinRulesParityTest`, and `functions/src/billing.test.ts` (22 server tests incl.
the unlock rules). Everything marked **[device]** or **[Play]** has NOT been run.

Use a license-tester Google account. Test subscriptions renew every 5 minutes (monthly) and expire after 6 renewals.

## Purchase
1. **[Play]** Buy monthly: sheet opens, Pro turns on within seconds, status card says "Tum Pro ho".
2. **[Play]** Buy yearly after monthly (plan change): old token is superseded, entitlement shows YEARLY.
3. **[Play]** Close the Play sheet: no message, buttons come back (USER_CANCELED).
4. **[Play]** Pending payment (test card "slow"): "Payment pending" message, Pro stays off; after it clears Pro turns on
   without any tap.
5. **[device]** Not signed in: buy shows "Pehle sign in karo"; nothing is sent.
6. **[device]** No network on the paywall: plans show the offline card with Retry; Buy is hidden.
7. **[device]** Buy with no network after the sheet (server unreachable): outcome "net nahi hai", then the 6-hourly sync or
   Restore finishes the job; the purchase is acknowledged by the server within 3 days (check Play Console).

## Account safety
8. **[Play]** Send account A's token to `verifyPurchase` as account B: `permission-denied`, nothing written.
9. **[Play]** Same token used by two accounts: second gets `already-exists`.
10. Sideloaded app that fakes a purchase: no entitlement document, so no Pro.
11. Edit the cached entitlement on a rooted phone to ACTIVE for ever: the next server snapshot overwrites it; server-side
    gates (duel quota, squad creation) never read the cache.

## Lifecycle
12. **[Play]** Cancel in Play: status "Pro <date> ko khatam hoga" and resubscribe button; Pro works until the date, then ends
    by itself while the app is open (EntitlementService timer).
13. **[Play]** Test card that fails renewal: GRACE card with "Payment theek karo" -> Play payments page; after hold: ON_HOLD,
    Pro off. Fix the payment: Pro returns.
14. **[Play]** Pause (if enabled), refund and revoke from Play Console: RTDN rewrites the entitlement within a minute;
    refund -> EXPIRED.
15. **[Play]** Missed notification (disable the topic): `refreshStaleEntitlements` fixes it within 6 hours.

## Restore
16. **[device]** Reinstall, sign in, open the paywall, tap "Purchase restore karo": Pro returns. Also returns on its own at
    app start (sync every 6 hours).
17. **[device]** Restore with no subscription: "Is Google account pe koi Pro subscription nahi mili".
18. **[device]** Restore while offline: "Net nahi hai".

## Offline and clock
19. **[device]** Pro, airplane mode, reboot: Pro still works (cache).
20. **[device]** Airplane mode past `accessUntilMs` on a cancelled plan: Pro ends at that moment. On a renewing plan it stays for
    the 3-day slack, then ends until online.
21. **[device]** Set the phone clock back 2 days while Pro: "Phone ka time galat lag raha hai", Pro features off; fix clock: back.
22. **[device]** Change time zone: no effect on Pro (times are absolute).

## Pro gating
23. Free: tap Squad / Strict Lock tile -> soft prompt (Pro dekho / Baad mein), nothing locked behind it, Baad mein closes it.
24. Free: switch Strict Lock ON in Settings -> prompt; nothing changes. Switch OFF an existing one -> works (unless the
    commitment rule blocks it).
25. Free with an existing focus schedule: can open the editor and remove ranges; adding or moving a range shows the prompt.
26. Pro ends while a Strict Lock is running: lock continues to its end.
27. Pro skin equipped, Pro ends: default skin shown; Pro again: skin returns.
28. **[server]** Free account creates a 4th duel in a day: refused by the server gate (once `createDuel` exists).

## Coins
29. Daily check-in twice: second says already claimed. Over the cap: grant trimmed, then refused.
30. Check that no screen, product id or function sells coins (`scripts/check-coin-money-separation.sh`).
