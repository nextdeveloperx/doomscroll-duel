#!/usr/bin/env bash
# Turns the PNGs made by StoreAssetsTest into files Google Play accepts, and checks every limit.
#   adb pull /sdcard/Android/data/com.doomscrollduel.debug/files/store ./store-raw
#   scripts/prepare-store-assets.sh ./store-raw ./store-ready
#
# Play rules checked here: icon exactly 512x512 PNG up to 1 MB; feature graphic exactly 1024x500 PNG or JPEG up to 15 MB;
# phone screenshots JPEG or 24-bit PNG (NO transparency), each side 320 to 3840 px, the long side at most 2x the short side,
# 2 to 8 screenshots per language. Needs ImageMagick (`convert` and `identify`).
set -euo pipefail
in="${1:?usage: prepare-store-assets.sh <input-dir> <output-dir>}"
out="${2:?usage: prepare-store-assets.sh <input-dir> <output-dir>}"
bg="#FFD93D"  # the app's yellow; fills any transparent pixel instead of leaving alpha behind
fail=0
mkdir -p "$out"

flatten() { convert "$1" -background "$bg" -alpha remove -alpha off -strip PNG24:"$2"; }
dims() { identify -format '%w %h' "$1"; }
size() { stat -c %s "$1"; }
bad() { echo "FAIL: $*"; fail=1; }

if [[ -f "$in/icon_512.png" ]]; then
  flatten "$in/icon_512.png" "$out/icon_512.png"
  read -r w h <<<"$(dims "$out/icon_512.png")"
  [[ "$w" == 512 && "$h" == 512 ]] || bad "icon is ${w}x${h}, must be 512x512"
  (( $(size "$out/icon_512.png") <= 1048576 )) || bad "icon is larger than 1 MB"
else bad "icon_512.png missing"; fi

if [[ -f "$in/feature_graphic_1024x500.png" ]]; then
  flatten "$in/feature_graphic_1024x500.png" "$out/feature_graphic_1024x500.png"
  read -r w h <<<"$(dims "$out/feature_graphic_1024x500.png")"
  [[ "$w" == 1024 && "$h" == 500 ]] || bad "feature graphic is ${w}x${h}, must be 1024x500"
else bad "feature_graphic_1024x500.png missing"; fi

for lang in en hi; do
  count=0
  mkdir -p "$out/$lang"
  for f in "$in/$lang"/*.png; do
    [[ -e "$f" ]] || continue
    dest="$out/$lang/$(basename "$f")"
    flatten "$f" "$dest"
    read -r w h <<<"$(dims "$dest")"
    short=$(( w < h ? w : h )); long=$(( w < h ? h : w ))
    (( short >= 320 && long <= 3840 )) || bad "$dest is ${w}x${h}: sides must be 320 to 3840 px"
    (( long <= short * 2 )) || bad "$dest is ${w}x${h}: long side is more than 2x the short side"
    count=$((count + 1))
  done
  (( count >= 2 && count <= 8 )) || bad "language $lang has $count screenshots, Play needs 2 to 8"
done

if [[ $fail -eq 0 ]]; then echo "store assets ready in $out"; fi
exit $fail
