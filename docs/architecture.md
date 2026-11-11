# RelayHarbor architecture

## v0.1.0
- Local-first: Room is the canonical message store.
- Preferences DataStore stores provider selections and URL (credentials MUST NOT be in this store).
- Compose UI presents chat, channels, score cards and settings.
- No runtime dependency on Synology, Dify, or a cloud provider.
- External webhook ingress is deliberately not exposed by the Android client.
- Future integrations: define HTTPS authenticated gateway profiles, sync adapter with idempotent message IDs,
  secure token storage, push registration, and Dify conversation providers.

## Trust boundary
Internet webhook -> optional cloud / self-hosted gateway -> authenticated Android sync / push.
Never place provider master API keys in clients; scope user tokens and use TLS.
Raw HTML and remote executable card scripts are not supported.
