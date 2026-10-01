# Doomscroll Duel (Android)

Currently contains the design system (`core/designsystem`) and the first six screens with fake data
(`feature/`), wired together in `navigation/NavGraph.kt`. Screens only see `*UiState` classes;
`feature/FakeData.kt` is replaced by Room-backed repositories later.

Screens: Home, Battle modes, New duel, Live duel, Result, Settings. Every screen has Studio previews
(390x844, plus 320x640 and 600x960 to check scaling).

Gradle scaffolding (Milestone 1) is not in this folder yet. The design system needs:

- `androidx.compose:compose-bom`, with `ui`, `foundation`, `animation`, `ui-tooling-preview` (and `ui-tooling` for debug)
- `androidx.compose.ui:ui-text` (variable font support, API 26+)
- `androidx.navigation:navigation-compose` (NavGraph)
- No `material3` and no `material-icons`: the theme and icons are custom.
- Unit tests: `junit`.
- `namespace` must be `com.doomscrollduel` (the code imports `com.doomscrollduel.R`).

Fonts (`res/font`) are SIL OFL 1.1, licences in `licenses/`.

## Added dependencies (blocking engine)
androidx.datastore:datastore-preferences, androidx.work:work-runtime-ktx, androidx.lifecycle (runtime/service),
androidx.savedstate, firebase-auth, firebase-firestore, firebase-functions, firebase-messaging,
kotlinx-coroutines-play-services. Server: `functions/` (Node 20, `npm test`).

com.android.billingclient:billing-ktx (7.1.x) is needed for `BillingManager`. Server: `@googleapis/androidpublisher`, `google-auth-library`.
