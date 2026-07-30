#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEST="$ROOT/gradle/wrapper/gradle-wrapper.jar"
TMP="$DEST.tmp"
URL="https://services.gradle.org/distributions/gradle-9.1.0-wrapper.jar"
EXPECTED="76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3"
mkdir -p "$(dirname "$DEST")"
cleanup() { rm -f "$TMP"; }
trap cleanup EXIT INT TERM

if [ -f "$DEST" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    EXISTING=$(sha256sum "$DEST" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    EXISTING=$(shasum -a 256 "$DEST" | awk '{print $1}')
  else
    echo "A SHA-256 utility (sha256sum or shasum) is required." >&2
    exit 1
  fi
  if [ "$EXISTING" = "$EXPECTED" ]; then
    printf 'Gradle wrapper already present and verified: %s\n' "$DEST"
    exec "$ROOT/gradlew" --version
  fi
  echo "Existing Gradle wrapper checksum is unexpected; replacing it." >&2
fi

if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 --connect-timeout 15 "$URL" -o "$TMP"
elif command -v wget >/dev/null 2>&1; then
  wget --tries=3 --timeout=15 -O "$TMP" "$URL"
else
  echo "Install curl or wget, then rerun this script." >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$TMP" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$TMP" | awk '{print $1}')
else
  echo "A SHA-256 utility (sha256sum or shasum) is required." >&2
  exit 1
fi
[ "$ACTUAL" = "$EXPECTED" ] || { echo "Gradle wrapper checksum mismatch." >&2; exit 1; }
mv "$TMP" "$DEST"
printf 'Installed and verified Gradle wrapper: %s\n' "$DEST"
exec "$ROOT/gradlew" --version
