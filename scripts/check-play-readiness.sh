#!/usr/bin/env bash
# Machine-checkable parts of Google Play review. See scripts/check_play_readiness.py for what it checks.
exec python3 "$(dirname "$0")/check_play_readiness.py" "$@"
