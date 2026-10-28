#!/bin/bash
# Packs dist/Encore.app into a disk image whose window shows Encore beside an Applications link
# over the branded background. Run ./scripts/bundle-macos.sh first.
#
#   ./scripts/make-dmg.sh dist/Encore-1.3.0-macos-arm64.dmg
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <image.dmg>" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="$1"
APP="$ROOT/dist/Encore.app"
DMGBUILD_VERSION="1.6.7"

if [ ! -d "$APP" ]; then
  echo "$APP is missing, run ./scripts/bundle-macos.sh first" >&2
  exit 1
fi

WORK="$ROOT/target/dmg"
mkdir -p "$WORK"

# dmgbuild writes the window's layout itself, so no Finder scripting is needed
if [ ! -x "$WORK/venv/bin/dmgbuild" ]; then
  python3 -m venv "$WORK/venv"
  "$WORK/venv/bin/pip" install --quiet "dmgbuild==$DMGBUILD_VERSION"
fi

# one TIFF with both sizes, so Retina screens get the sharp one
tiffutil -cathidpicheck \
  "$ROOT/assets/macos/dmg-background.png" \
  "$ROOT/assets/macos/dmg-background@2x.png" \
  -out "$WORK/background.tiff" >/dev/null

rm -f "$IMAGE"
"$WORK/venv/bin/dmgbuild" \
  -s "$ROOT/scripts/dmg-settings.py" \
  -D app="$APP" \
  -D background="$WORK/background.tiff" \
  -D icon="$ROOT/assets/macos/encore.icns" \
  "Encore" "$IMAGE"

echo "built $IMAGE"
