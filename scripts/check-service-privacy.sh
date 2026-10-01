#!/usr/bin/env bash
# Fails if the accessibility service code uses any API that could read what the user sees or act on the screen
# beyond what is allowed. Comment lines are ignored, so the privacy contract in KDoc does not trip it.
# Run in CI and before every release:  scripts/check-service-privacy.sh
#
# What is allowed:
#   - tracking/service, tracking/detector, blocking/: no text, descriptions, children, parents, gestures or node
#     actions. They MAY press Back/Home (performGlobalAction), which is how the blocker leaves a reel screen.
#   - tracking/service/ReelScreenProbe.kt is the ONLY file that may look at the window tree, and then only to ask
#     "is there a view with this layout id?". It may not read anything else from it.
set -euo pipefail
cd "$(dirname "$0")/.."

base=app/src/main/kotlin/com/doomscrollduel
probe=$base/tracking/service/ReelScreenProbe.kt
dirs=("$base/tracking/service" "$base/tracking/detector" "$base/blocking")

# Anything that reads content or acts on nodes. performGlobalAction is NOT in this list on purpose.
forbidden='\.text\b|getText\b|contentDescription|getContentDescription|\.getChild|getChildCount|\.child\(|getParent\b|\.parent\b|rootInActiveWindow|\.windows\b|findAccessibilityNodeInfos|performAction|dispatchGesture|AccessibilityNodeInfo\.(obtain|text)|\.hintText|\.tooltipText|\.paneTitle|\.extras\b|\.getText'
# What even the probe must never use (it may use rootInActiveWindow and findAccessibilityNodeInfosByViewId).
probe_forbidden='\.text\b|getText\b|contentDescription|getContentDescription|\.getChild|getChildCount|\.child\(|getParent\b|\.parent\b|\.windows\b|findAccessibilityNodeInfosByText|performAction|performGlobalAction|dispatchGesture|\.hintText|\.tooltipText|\.paneTitle|\.extras\b|\.getText'

status=0
strip_comments() { grep -nvE '^[[:space:]]*(//|/\*|\*)' "$1" || true; }

while IFS= read -r file; do
  if [[ "$file" == "$probe" ]]; then
    hits=$(strip_comments "$file" | grep -E "$probe_forbidden" || true)
  else
    hits=$(strip_comments "$file" | grep -E "$forbidden" || true)
  fi
  if [[ -n "$hits" ]]; then
    echo "FORBIDDEN API in $file:"; echo "$hits"; status=1
  fi
done < <(find "${dirs[@]}" -name '*.kt')

# Nobody else may touch the window tree or look up views by id.
others=$(grep -rnE 'rootInActiveWindow|findAccessibilityNodeInfosByViewId' app/src/main/kotlin | grep -v "$probe" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|/\*|\*)' || true)
if [[ -n "$others" ]]; then echo "Only ReelScreenProbe may look at the window tree:"; echo "$others"; status=1; fi

# The XML config must keep gestures off, keep the four packages, and list no unexpected event types.
cfg=app/src/main/res/xml/reel_accessibility_service.xml
grep -q 'android:canPerformGestures="false"' "$cfg" || { echo "canPerformGestures must be false"; status=1; }
grep -q 'android:packageNames=' "$cfg" || { echo "packageNames filter missing"; status=1; }
grep -qE 'typeAllMask|typeViewTextChanged|typeViewClicked|typeNotificationStateChanged|typeViewFocused' "$cfg" && { echo "unexpected event types"; status=1; }
exit $status
