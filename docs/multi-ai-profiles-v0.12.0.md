# RelayHarbor v0.12.0 — Multiple AI providers

## What is supported
- **設定 → AI接続先の管理**: securely create, edit, delete and list up to 30 named AI profiles.
- Types: OpenAI Responses API (provider type \`openai\`), OpenAI-compatible chat completions API (\`compatible\`; includes services offering a compatible endpoint), and Dify Chat API (\`dify\`).
- Multiple models and different API keys or endpoints may be registered, including more than one account/model at the same provider.
- **チャット → 🤖 selector**: choose default (old AI setting), off, or an individually configured AI for each channel. Choices persist across restarts and are isolated by Relay endpoint.
- Each AI profile retains separate chat context and Dify conversation IDs within the channel; old untargeted messages remain available to the legacy provider during migration.
- AI replies show their configured provider display names; threads remain device-local and do not trigger AI calls.

## Architecture and security
- API keys are entered temporarily in the app, POSTed/PATCHed through the existing HTTPS authenticated Relay connection, and encrypted with Fernet before writing SQLite on the Relay host.
- Neither the API keys nor the encryption key are returned by the listing API or embedded in the APK. Saved provider configurations **cannot** be decrypted if the Fernet key is lost or rotated; back up the key in a separate secure location.
- Existing DIFY_API_* and OPENAI_API_* environment variables still work unchanged. The old \`/v1/chat\` request without profile_id remains backward compatible.
- New endpoints: \`GET/POST /v1/ai-profiles\`, \`PATCH/DELETE /v1/ai-profiles/{id}\` (and \`POST /v1/ai-profiles/{id}/update\` for Android's HTTP implementation).
- Creating AI profiles requires a server-side \`RELAY_AI_ENCRYPTION_KEY\`; old Relay versions do not support this endpoint.
- User-created base URLs must be public HTTPS hosts on port 443; localhost/private hosts are rejected, and DNS is checked again before sending. No redirects or system proxies are followed. Outbound firewall restrictions are strongly recommended for SSRF defense in depth.
- Credential editing is privileged by RELAY_CLIENT_TOKEN. This remains a **single-user self-hosted** system and MUST NOT be deployed as public multi-user SaaS.
- No API keys in Room or Android preferences. Room v5 → v6 adds \`messages.aiProfileId\` with default empty string to preserve all existing histories. Provider selection is non-secret SharedPreferences metadata.

## Synology NAS upgrade
1. Back up the Relay SQLite volume and your existing .env before upgrading.
2. Install the updated Relay image or deploy the updated source. The database table is created without dropping previous events/integrations.
3. Generate the Fernet key on a trusted machine (after installing \`cryptography\`):
   \`\`\`bash
   python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
   \`\`\`
4. In the Relay deployment's private \`relay/.env\`, set:
   \`\`\`dotenv
   RELAY_AI_ENCRYPTION_KEY=<the-output-above>
   \`\`\`
   Never commit that value to GitHub. Keep a secure copy outside the NAS volume.
5. Restart only the Relay container; then open RelayHarbor → 設定 → AI接続先の管理 → 再取得. Existing Webhooks/RSS and legacy env-based AI settings continue working.
6. Register one or more provider profiles with the correct API URL, model and key. In チャット pick the AI independently for each channel.

## QA
- Relay integration tests verify multiple independently encrypted keys, CRUD permissions, API key redaction, invalid URLs, profile-specific model dispatch and legacy endpoint handling.
- Android CI compiles and runs unit tests. Unit tests cover profile validation.
- Manual tests recommended: existing v0.11.0 app update without uninstall, local-only mode, losing the network, legacy env-only server, Fold cover/expanded layout, selected AI persistence and mixed profiles in different channels.
- **Limitations:** no Anthropic-native API support (only OpenAI-compatible offerings), no cross-device sync of channel selections, no access to localhost/private URLs for user-created profiles, no multi-user authentication and no remote synchronization of thread replies.
