# Firestore rules tests

Runs `firebase/firestore.rules` on the Firestore emulator (needs JDK 21 or newer on PATH / JAVA_HOME).

    cd firebase/rules-test
    npm install
    RULES=../firestore.rules npm test      # PowerShell: $env:RULES="..\firestore.rules"; npm test

Covers profile + directory creation, the People list, one invite per person, accepting an invite (both friend edges),
refusing a forged name, and the push-token write. The "PERMISSION_DENIED" lines in the output come from the tests that
are meant to be refused.
