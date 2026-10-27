#!/usr/bin/env bash
# Builds Encore in release mode for Apple Silicon and assembles dist/Encore.app, then signs it
# ad hoc so it opens.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

version="$(sed -n 's/^version = "\(.*\)"/\1/p' Cargo.toml | head -n 1)"
app="dist/Encore.app"

cargo build --locked --release --package encore --target aarch64-apple-darwin

rm -rf "$app"
mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources/icons"

cp target/aarch64-apple-darwin/release/encore "$app/Contents/MacOS/encore"
chmod +x "$app/Contents/MacOS/encore"
cp assets/macos/encore.icns "$app/Contents/Resources/"
cp COPYING "$app/Contents/Resources/LICENSE"
cp THIRD-PARTY.md "$app/Contents/Resources/"
cp assets/fonts/LICENSE.txt "$app/Contents/Resources/LICENSE.Inter"
cp assets/icons/LICENSE "$app/Contents/Resources/icons/LICENSE"
for licence in assets/icons/*/LICENSE; do
  pack="$(basename "$(dirname "$licence")")"
  cp "$licence" "$app/Contents/Resources/icons/LICENSE.$pack"
done
sed "s/@VERSION@/$version/g" assets/macos/Info.plist > "$app/Contents/Info.plist"

codesign --force --deep --sign - "$app"
echo "built $app ($version)"
