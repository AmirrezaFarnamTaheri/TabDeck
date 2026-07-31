# ADR-0001: Durable source identity

**Status:** Accepted

External browser tab IDs are session-scoped and may be reused after restart. TabDeck therefore stores its own UUID as the durable primary key. Connector identity equality uses connector/device/browser/profile/session/window/external-tab attributes; `firstSeenAt` remains metadata and never participates in equality. Raw external IDs are transformed into a bounded opaque ID containing a one-way session fingerprint. Legacy unscoped IDs remain readable but cannot authorize destructive complete-snapshot reconciliation.
