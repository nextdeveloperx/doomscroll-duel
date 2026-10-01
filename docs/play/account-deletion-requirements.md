# What the web deletion page must contain (Google Play)

Google Play asks every app that lets people create an account to offer deletion **inside the app** and also to give a
**public web link** where a person can ask for deletion without installing the app. You enter that link in
Play Console > App content > Data safety (the "Delete account URL" / data deletion section).

## The page must
1. Be public, reachable without signing in, on a normal web address (not a PDF, not a login wall, not a Google Doc).
2. Name the **app** exactly as in the Play listing and the **developer** (the same name as in Play Console).
3. Give the **steps** to request deletion, clearly. In-app steps and, for people without the app, a working way to ask
   (an email address is accepted in practice; a web form is the safest because reviewers like a visible "request" action).
4. Say **which data is deleted** and **which is kept, why, and for how long** (here: nothing kept except 30 days of
   hashed function logs; Google Play billing records are held by Google).
5. Say **how long it takes**.
6. Be consistent with the Privacy Policy, the in-app screen, and the Data safety answers.
7. Stay online as long as the app is on Play. If the address changes, update Play Console the same day.

## Ready-made page
`docs/play/account-deletion-page.md` is written to those rules. `python3 scripts/build-legal-pages.py` turns it into
`web/delete-account.html` (together with `web/privacy.html` and `web/terms.html`). Host the three files at
`{{SITE_URL}}/delete-account`, `/privacy`, `/terms` (Firebase Hosting is the simplest), then put the same addresses in
`app/src/main/res/values/legal_links.xml`.

## Things you must be able to do (the page promises them)
- Read the support mailbox and reply within 7 days.
- Verify the person before deleting (reply from the account email, or a code you send to it).
- For an email request you cannot self-serve: deletion is done by running the same function logic as the app
  (`functions/src/deletion.ts`) for that account ID. Add a small admin script before launch if you expect email requests.
- Do not enable Firestore backups or point-in-time recovery without updating the policy, because the policy says there
  are no backups.
