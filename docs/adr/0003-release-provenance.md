# ADR-0003: Secretless community release provenance

**Status:** Accepted

TabDeck publishes one APK through GitHub Releases and does not maintain an application-store channel. The workflow requires no protected environment or repository secrets. A repository-published community key provides stable Android package signatures so later GitHub APKs can upgrade earlier ones.

Because the key is intentionally public, it is not treated as exclusive publisher authentication. Release trust comes from the official repository, immutable source/tag binding, the release manifest, and SHA-256 checksums. The workflow verifies the public certificate fingerprint, APK alignment and signature, archive integrity, and source/tag provenance before publication.
