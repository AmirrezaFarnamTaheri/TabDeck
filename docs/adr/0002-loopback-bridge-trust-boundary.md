# ADR-0002: Loopback bridge trust boundary

**Status:** Accepted

The HTTP bridge binds only to loopback and accepts only loopback clients. Desktop clients must use an explicit ADB port forward. Direct LAN exposure is disabled because bearer tokens, Origin checks, and private-address filtering do not provide peer authentication or transport confidentiality. Reintroducing LAN transport requires TLS with peer authentication, explicit user opt-in, peer/address allowlisting, certificate lifecycle management, and immediate token/peer revocation.
