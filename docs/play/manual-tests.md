# Manual tests for the disclosure and account deletion (not run yet; do them on a real phone)

## Accessibility disclosure
1. Fresh install, open the app: Home shows the banner "Pehle ek zaroori jaankari padho" (not "Counting band hai").
2. Tap PADHO: the full disclosure opens. Nothing from Android Settings opens yet.
3. Press the system Back button: you return to Home; no consent recorded (banner still there).
4. Open it again, tap NOT NOW: same result. Both buttons are the same size and both readable at 200% font size.
5. Open it again, tap AGREE: Android's Accessibility settings open. Switch the service on. Back in the app the banner is gone.
6. Open Instagram Reels and swipe: the count goes up.
7. **Consent gate:** uninstall and reinstall (consent is cleared), switch the service on in Android Settings > Accessibility
   directly, swipe reels: the count stays 0 and the banner asks for the disclosure. Agree, swipe again: it counts.
8. Settings > "Accessibility jaankari dobara padho": the text opens with a single "Wapas" button; nothing is requested.
9. TalkBack: headings are announced, the English summary is reachable, buttons say AGREE and NOT NOW.
10. Rotate to landscape and use a small phone: the text scrolls and the buttons stay visible at the bottom.
11. Change `DISCLOSURE_VERSION` in a debug build: the banner returns and counting stops until the person agrees again.

## Delete account (needs sign-in and the deployed `deleteAccount` function)
1. Settings > Account delete karo: the lists and (if Pro) the subscription card show; the red button is disabled.
2. Type "delet": still disabled. Type "delete" and tick the box: enabled. Untick: disabled.
3. "RUKO, WAPAS JAO" returns to Settings and nothing is deleted.
4. Airplane mode, delete: "Net nahi hai", nothing deleted, form stays.
5. Sign in more than 5 minutes ago, delete: "dobara sign in" message, nothing deleted.
6. Normal delete: "Delete ho raha hai" with Back disabled, then "Account delete ho gaya". In the Firebase console check that
   `users/{uid}`, `usernames`, `wallets`, `entitlements`, `purchaseTokens`, `unlockRequests`, Storage `proofs/{uid}/` and the
   Auth user are gone, and that the friend's `users/{friend}/friends/{uid}` edge is gone and a duel now lists "deleted".
7. Make a step fail (revoke Storage permission), delete: error "Dobara try karo"; the Auth user still exists; fix the
   permission, delete again: it completes (idempotent).
8. With a Strict Lock running, delete the account: the lock is still running afterwards (local lock state is not erased).
9. Pro active: the subscription card is shown before and after; "PLAY MEIN SUBSCRIPTION DEKHO" opens Google Play.
10. Open the public deletion page in a private browser window with no login: it shows the steps and the contact.
