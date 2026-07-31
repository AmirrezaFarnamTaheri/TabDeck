# ADR-0003: Release provenance and signing identity

**Status:** Accepted

Every published artifact set is bound to an immutable source commit and the owner-approved Android signing certificate. The release workflow verifies that the tag resolves to the checked-out commit, verifies APK/AAB signatures, extracts both certificate SHA-256 fingerprints, compares them with the protected expected fingerprint, and records commit/tag/fingerprint in the release manifest before attestation and publication.
