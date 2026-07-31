# Secretless community release signing

TabDeck GitHub Releases use a repository-published community signing key so the release workflow needs no GitHub environments or secrets and Android can upgrade one community build over another.

This key is intentionally public. It provides package-signature continuity, not exclusive publisher identity. Verify downloads through the official GitHub Release, SHA-256 checksums, source commit, and release manifest.

- alias: `tabdeck-community`
- store/key password: `tabdeck-community`
- certificate SHA-256: `8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7`

Do not use this key for an application-store or privately controlled production channel. A private signing identity would require a separate distribution workflow.
