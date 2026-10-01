#!/usr/bin/env bash
# Every dare in DareCatalog needs a user-facing string, and no string may exist without a dare.
set -euo pipefail
cd "$(dirname "$0")/.."
catalog=app/src/main/kotlin/com/doomscrollduel/domain/challenge/dare/DareCatalog.kt
strings=app/src/main/res/values/strings.xml
a=$(grep -oE 'Dare\("[a-z_]+"' "$catalog" | sed -E 's/Dare\("([a-z_]+)"/\1/' | sort)
b=$(grep -oE 'name="dare_[a-z_]+"' "$strings" | sed -E 's/name="dare_([a-z_]+)"/\1/' | sort)
if [[ "$a" != "$b" ]]; then
  echo "Dare catalog and strings.xml differ:"; diff <(echo "$a") <(echo "$b") || true; exit 1
fi
echo "dare strings match the catalog ($(echo "$a" | wc -l) dares)"
