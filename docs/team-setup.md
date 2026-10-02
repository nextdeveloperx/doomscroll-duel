# Team setup (so Firebase and the database work on your machine)

Firebase project: `brainpal-b3119` (shared with brainPAL). The app config `app/google-services.json` is committed, so a fresh clone talks to the
same project. Secrets are NOT in git and you must never commit them: `keystore.properties`, `*.jks`, `local.properties`.

1. **Tools**: Android Studio (JDK 17 for Gradle), JDK 21+ and Node 20+ for the Firestore emulator tests, Firebase CLI (`npm i -g firebase-tools`, `firebase login`).
2. **Google sign-in needs your debug key registered.** Every developer's debug keystore has a different SHA-1; sign-in fails until it is added:
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` then
   `firebase apps:android:sha:create 1:1029990588865:android:658272bb6d59ec860acf40 <SHA1-without-colons> --project brainpal-b3119`
   (debug app id `com.doomscrollduel.debug`; release app id `com.doomscrollduel` is `1:1029990588865:android:6011c394d7b910840acf40`).
3. **Database rules** live in `firebase/firestore.rules` and are deployed with `firebase deploy --only firestore:rules --project brainpal-b3119`.
   Test them first: `cd firebase/rules-test && npm install && RULES=../firestore.rules npm test` (JDK 21+ on PATH). Change rules and the app together:
   the app is written against the rules in this repo; an old rules file gives "permission denied" in the app.
4. **Cloud Functions** (`functions/`) are written but NOT deployed (the project is not on the Blaze plan); the app works without them.
5. **Voice relay (optional)**: for Broadcast voice across strict phone networks add `TURN_URLS`, `TURN_USERNAME`, `TURN_CREDENTIAL` to `local.properties`.
6. **Release signing**: copy `keystore.properties.example` to `keystore.properties` and point it at the upload key (kept outside git). Its SHA-1 is already in Firebase.
7. Secret hygiene: the Firebase API key in `google-services.json` is a client identifier, but restrict it (Cloud Console, APIs and services, Credentials)
   to Android apps `com.doomscrollduel` / `.debug` with the registered SHA-1s.
