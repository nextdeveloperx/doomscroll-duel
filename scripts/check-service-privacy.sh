#!/usr/bin/env bash
# Fails if the accessibility service code uses any API that could read what the user sees or act on the
# screen. Comment lines are ignored, so the privacy contract in the service's KDoc does not trip it.
# Run in CI and before every release:  scripts/check-service-privacy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

dirs=(
  app/src/main/kotlin/com/doomscrollduel/tracking/service
  app/src/main/kotlin/com/doomscrollduel/tracking/detector
)
forbidden='\.text\b|getText\b|contentDescription|getContentDescription|\.getChild|getChildCount|\.child\(|getParent\b|\.parent\b|rootInActiveWindow|\.windows\b|findAccessibilityNodeInfos|performAction|dispatchGesture|AccessibilityNodeInfo\.(obtain|text)|\.hintText|\.error\b|\.tooltipText|\.paneTitle|\.extras\b|\.getText'

status=0
while IFS= read -r file; do
  # drop whole-line comments (//, /*, *, */) before searching
  hits=$(grep -nvE '^[[:space:]]*(//|/\*|\*)' "$file" | grep -E "$forbidden" || true)
  if [[ -n "$hits" ]]; then
    echo "FORBIDDEN API in $file:"
    echo "$hits"
    status=1
  fi
done < <(find "${dirs[@]}" -name '*.kt')

# The XML config must keep gestures off and the package list to the four apps.
cfg=app/src/main/res/xml/reel_accessibility_service.xml
grep -q 'android:canPerformGestures="false"' "$cfg" || { echo "canPerformGestures must be false"; status=1; }
grep -q 'android:packageNames=' "$cfg" || { echo "packageNames filter missing"; status=1; }
grep -qE 'typeAllMask|typeViewTextChanged|typeViewClicked|typeNotificationStateChanged' "$cfg" && { echo "unexpected event types"; status=1; }
exit $status
