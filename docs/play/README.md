# Google Play review pack

| File | What it is |
|---|---|
| `privacy-policy.md` | Privacy Policy (host it as `/privacy`) |
| `terms-of-use.md` | Terms of Use with the content rules (host as `/terms`) |
| `account-deletion-page.md` | Public deletion page (host as `/delete-account`) |
| `account-deletion-requirements.md` | What the web deletion page must contain and what you must be able to do |
| `data-safety.md` | Answers for the Data safety form |
| `accessibility-declaration.md` | Answers for the AccessibilityService declaration and the **video script** |
| `content-rating.md` | Answers for the IARC questionnaire and the target audience form |
| `store-listing.md` | App name, short and full descriptions (English and Hinglish) |
| `review-checklist.md` | Rejection risks with status, launch blockers, and the age (13/16/18) note |

## Fill-ins you must provide
The texts contain `{{PLACEHOLDERS}}`. Copy `scripts/legal-config.template.json` to `scripts/legal-config.json`, fill it,
and run `python3 scripts/build-legal-pages.py`. It writes `web/privacy.html`, `web/terms.html` and `web/delete-account.html`
with your values and fails (in `--release` mode) if any placeholder is left. For the Play Console boxes, copy the same
text and replace the placeholders by hand.

| Placeholder | Meaning |
|---|---|
| `{{DEVELOPER_NAME}}` | Legal name of the developer or company, same as in Play Console |
| `{{POSTAL_ADDRESS}}` | Postal address (Play also shows a developer address) |
| `{{SUPPORT_EMAIL}}` | A mailbox you read |
| `{{GRIEVANCE_OFFICER}}` | Name and email of the grievance contact |
| `{{SITE_URL}}` | Where you host the pages, for example `https://yourdomain.in` |
| `{{DATA_REGION}}` | Where Firestore is hosted, for example "Mumbai, India (asia-south1)" |
| `{{EFFECTIVE_DATE}}` | Date the policy starts |
| `{{GOVERNING_COURTS}}` | City whose courts apply |

## Promises the texts make that you must keep
7-day reply and 30-day deletion by email; reports acted on quickly; no backups of user data; 30-day log retention; the
7-day auto-delete of dare proofs; "no ads, no sale, no sharing with advertisers". If any of these will not be true,
change the text first.

## Not legal advice
These are drafts written from what the app does. Have a lawyer who knows Indian data protection law read the Privacy
Policy, the Terms and the age decision before launch.
