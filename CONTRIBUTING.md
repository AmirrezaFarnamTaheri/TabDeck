# Contributing

## Principles

- Preserve Android-first behavior and explicit user authorization.
- Do not claim universal cross-browser tab access.
- Keep destructive actions previewable, bounded, confirmable, and recoverable where technically possible.
- Sanitize at input, persistence, export, and launch boundaries.
- Keep large-library operations database-backed and chunked.
- Maintain compatibility migrations for persisted and serialized formats.

## Before submitting a change

```bash
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py
./bootstrap-wrapper.sh
./gradlew clean test lintDebug assembleDebug
```

Add tests for URL, query, backup, migration, dedupe, connector, or export behavior changed by the patch. Update the relevant document and changelog when behavior is user-visible.

Do not commit keystores, tokens, `local.properties`, generated build output, caches, or the Gradle wrapper JAR produced by the bootstrap script.
