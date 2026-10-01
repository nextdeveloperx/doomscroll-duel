#!/usr/bin/env python3
"""Checks the things Google Play review looks at that can be checked by a machine.

  python3 scripts/check_play_readiness.py                 normal run (also part of CI)
  python3 scripts/check_play_readiness.py --update-hash   after you deliberately changed the Accessibility disclosure
  python3 scripts/check_play_readiness.py --release       also refuses placeholders and the .invalid web address
"""
import hashlib
import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
KT = ROOT / "app/src/main/kotlin/com/doomscrollduel"
failures = []


def fail(msg):
    failures.append(msg)


def read(p):
    return Path(p).read_text(encoding="utf-8")


def strings(*files):
    out = {}
    for f in files:
        for name, value in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', read(RES / "values" / f), flags=re.S):
            out[name] = html.unescape(value)
    return out


# 1. The Accessibility disclosure text is pinned. Changing it needs a version bump and a new Play video.
disc_file = RES / "values/disclosure_strings.xml"
hash_file = ROOT / "docs/play/disclosure.sha256"
digest = hashlib.sha256(disc_file.read_bytes()).hexdigest()
if "--update-hash" in sys.argv:
    hash_file.write_text(digest + "\n")
    print("disclosure hash updated:", digest)
    print("Remember: bump AccessibilityConsent.DISCLOSURE_VERSION, update the Play video, the store listing and the policy.")
elif not hash_file.exists() or hash_file.read_text().strip() != digest:
    fail("disclosure_strings.xml changed. Bump AccessibilityConsent.DISCLOSURE_VERSION, re-check privacy policy, store "
         "listing and Play video, then run: python3 scripts/check_play_readiness.py --update-hash")

# 2. The disclosure says what Google requires it to say.
disc = " ".join(strings("disclosure_strings.xml").values()).lower()
required = {
    "names the API": "accessibility",
    "names Instagram": "instagram", "names YouTube": "youtube", "names Facebook": "facebook", "names Snapchat": "snapchat",
    "says messages are not read": "messages",
    "says passwords are not read": "passwords",
    "says no sharing": "share",
    "says no ads": "ads",
    "says it can be turned off": "band kar sakte",
    "has an English summary": "does not read your messages",
}
for what, needle in required.items():
    if needle not in disc:
        fail(f"disclosure does not say it ({what}): missing '{needle}'")
dstrings = strings("disclosure_strings.xml")
if dstrings.get("disc_agree", "").strip().upper() != "AGREE" or dstrings.get("disc_not_now", "").strip().upper() != "NOT NOW":
    fail("disclosure buttons must be AGREE and NOT NOW")

# 3. Service configuration.
cfg = read(RES / "xml/reel_accessibility_service.xml")
if 'android:isAccessibilityTool="false"' not in cfg:
    fail('service must declare android:isAccessibilityTool="false" (this is not an accessibility tool)')
if 'android:canPerformGestures="false"' not in cfg:
    fail("service must not be able to perform gestures")
pk = re.search(r'android:packageNames="([^"]+)"', cfg)
packages = sorted(pk.group(1).split(",")) if pk else []
if packages != sorted(["com.instagram.android", "com.google.android.youtube", "com.facebook.katana", "com.snapchat.android"]):
    fail(f"service packages changed: {packages}. The disclosure, policy and declaration name exactly four apps")

# 4. Permissions.
manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
permissions = set(re.findall(r'<uses-permission android:name="([^"]+)"', manifest))
forbidden = {
    "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
    "android.permission.READ_CALL_LOG", "android.permission.READ_CONTACTS", "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION", "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO", "android.permission.CAMERA",
}
for p in sorted(permissions & forbidden):
    fail(f"permission {p} is not allowed (Play restricts it or the policy says we never collect it)")
if 'android:allowBackup="false"' not in manifest:
    fail('android:allowBackup must be "false"')

# 4b. Analytics and crash reports must be off until the person says yes, and the advertising ID must not be collected.
if 'com.google.android.gms.permission.AD_ID" tools:node="remove"' not in manifest:
    fail("manifest must remove the AD_ID permission (the app has no ads): tools:node=\"remove\"")
for key in ("firebase_analytics_collection_enabled", "firebase_crashlytics_collection_enabled", "google_analytics_adid_collection_enabled"):
    if not re.search(r'android:name="%s"\s+android:value="false"' % key, manifest):
        fail(f"manifest must set {key} to false (collection stays off until the person allows it)")
kt_sources = "\n".join(read(f) for f in KT.rglob("*.kt"))
for f in KT.rglob("*.kt"):
    text = read(f)
    if re.search(r"\bsetUserId\s*\(", text):
        fail(f"{f.relative_to(ROOT)} sets an analytics or crash user id; no user id may ever be set")
    if "setUserProperty(" in text and f.name not in ("FirebaseAnalyticsSink.kt", "AnalyticsEvent.kt"):
        fail(f"{f.relative_to(ROOT)} sets a user property; only the Analytics wrapper may (plan = free or pro)")
if re.search(r"FirebaseAnalytics\.getInstance\([^)]*\)\.logEvent|FirebaseCrashlytics\.getInstance\(\)\.(log|setUserId|setCustomKey)\(", kt_sources):
    fail("use the Analytics wrapper and CrashReporter, not Firebase directly")

# 4c. Release build: shrinking on, no secrets in the build file.
gradle = read(ROOT / "app/build.gradle.kts")
if "isMinifyEnabled = true" not in gradle or "isShrinkResources = true" not in gradle:
    fail("release build must turn on R8 minify and shrinkResources")
if re.search(r'(storePassword|keyPassword)\s*=\s*"[^"]+"', gradle):
    fail("a signing password is written in app/build.gradle.kts; keep it in keystore.properties or environment variables")

# 5. The system Accessibility screen is opened only after consent.
call = re.compile(r"\bopenAccessibilitySettings\s*\(")
for f in KT.rglob("*.kt"):
    text = read(f)
    if f.name == "SystemIntents.kt" or not call.search(text):
        continue
    if "mayOpenSettings" not in text and "viewModel.agree()" not in text:
        fail(f"{f.relative_to(ROOT)} opens Accessibility settings without checking consent (use mayOpenSettings or the disclosure)")
if "AccessibilityConsent.mayProcessEvents" not in read(KT / "tracking/service/ReelAccessibilityService.kt"):
    fail("ReelAccessibilityService must drop events until consent")

# 6. Wording: gambling and medical words in everything the user can read.
app_text = strings("strings.xml", "ds_strings.xml", "disclosure_strings.xml")
listing = read(ROOT / "docs/play/store-listing.md")
listing_blocks = re.findall(r"<!--LISTING-START-->(.*?)<!--LISTING-END-->", listing, flags=re.S)
listing_text = "\n".join(listing_blocks)
# The one allowed sentence that denies medical use.
listing_scan = listing_text.replace("does not diagnose, treat or cure anything", "").replace("kisi bimari ka ilaaj ya nidaan nahi karta", "")
gambling = re.compile(r"\b(bet|bets|betting|wager|wagers|stake|stakes|jackpot|odds|casino|winnings|gamble|gambling)\b", re.I)
medical = re.compile(r"\b(addict\w*|cure[sd]?|treat(?:ment|s|ed)?|therap\w+|detox\w*|depress\w+|anxiety|adhd|mental health|clinical\w*|diagnos\w+|ilaaj|bimari|nasha)\b", re.I)
absolute = re.compile(r"\b(guarantee[sd]?|clinically proven|scientifically proven|#1|best app)\b", re.I)
for name, value in app_text.items():
    if gambling.search(value):
        fail(f"gambling word in app string {name}: '{gambling.search(value).group(0)}'")
    if medical.search(value):
        fail(f"medical word in app string {name}: '{medical.search(value).group(0)}'")
for rx, label in ((gambling, "gambling"), (medical, "medical"), (absolute, "absolute claim")):
    m = rx.search(listing_scan)
    if m:
        fail(f"{label} word in store listing: '{m.group(0)}'")

# 7. Store listing lengths.
for title, body in re.findall(r"### (.+?)\n```\n(.*?)\n```", listing_text, flags=re.S):
    limit = 30 if title.startswith("App name") else 80 if title.startswith("Short") else 4000
    if len(body) > limit:
        fail(f"store listing '{title}' is {len(body)} characters, limit {limit}")

# 8. Release mode: nothing left to fill in.
if "--release" in sys.argv:
    links = read(RES / "values/legal_links.xml")
    if ".invalid" in links:
        fail("legal_links.xml still points to doomscrollduel.invalid; host the pages and put the real address in")
    web = ROOT / "web"
    pages = ["privacy.html", "terms.html", "delete-account.html"]
    for page in pages:
        p = web / page
        if not p.exists():
            fail(f"web/{page} missing; run scripts/build-legal-pages.py")
        elif "{{" in read(p):
            fail(f"web/{page} still has {{{{placeholders}}}}; fill scripts/legal-config.json")

if failures:
    print("PLAY READINESS: FAILED")
    for f in failures:
        print(" -", f)
    sys.exit(1)
print("play readiness checks passed" + (" (release mode)" if "--release" in sys.argv else ""))
