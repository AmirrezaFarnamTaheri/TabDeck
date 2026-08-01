#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=${TMPDIR:-/tmp}/tabdeck-core-checks.jar
kotlinc \
  "$ROOT/tools/ComposeRuntimeStubs.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/model/Models.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/data/BackupInputClassifier.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/engine/SourceIdentity.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/engine/MaintenancePolicy.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/engine/UrlNormalizer.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/engine/UrlExtractor.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/engine/DedupeEngine.kt" \
  "$ROOT/app/src/main/java/com/tabdeck/app/data/TabExportCodec.kt" \
  "$ROOT/tools/CoreChecks.kt" \
  -include-runtime -d "$OUT"
java -jar "$OUT"
rm -f "$OUT"
