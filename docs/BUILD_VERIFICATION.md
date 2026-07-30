# TabDeck v1 build and verification record

## Evidence levels

### Executed in the source-preparation environment

- Version synchronization checks.
- Pure Kotlin core harness covering URL extraction/validation/normalization, duplicate behavior, browser mapping, and export hardening.
- XML, JSON, HTML, JavaScript, shell, embedded XAML, UTF-8/control-byte, Kotlin-delimiter, Compose-icon-import, manifest, extension, dependency-coordinate, compatibility-contract, and secret-pattern checks.
- Deterministic ZIP/XPI construction, duplicate-entry checks, and CRC verification.

### Enforced by GitHub Actions

Pull-request and push CI performs:

- JDK 17 / Gradle 9.1.0 setup;
- Android SDK 36 setup;
- wrapper checksum verification;
- unit tests;
- Android lint;
- debug APK assembly;
- report and artifact upload.

Tagged release CI additionally performs:

- exact tag/product/extension version agreement;
- environment-only release signing;
- release unit tests and lint;
- signed APK and AAB assembly;
- APK signature verification and AAB JAR-signature verification;
- deterministic release packaging;
- checksums and release manifest generation;
- provenance attestation;
- GitHub Release publication.

## Current environment limitation

The source-preparation container does not provide a usable Android SDK/Gradle dependency path, emulator, signing keystore, or native PowerShell/WPF runtime. Therefore no claim is made that an Android APK was compiled or signed locally here. Final installable artifacts are release-workflow outputs and must not be inferred from source-only archives.

## Release acceptance rule

A source-only validation pass is necessary but not sufficient for release. Publication requires a green tagged release workflow and verified signed Android artifacts.
