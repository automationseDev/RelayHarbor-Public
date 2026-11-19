# AIモデルサマリー — RelayHarbor v0.13.3

## Purpose and scope
The Android app offers **設定 → AIモデルサマリー**, below AI connection management.
The dashboard shows local Relay-mediated API requests only, aggregated per model:
request/success/error counts, input/output tokens, unreported-usage calls,
average response latency, JST daily chart, and optional user-rate-based
estimated USD usage. It does not represent usage from ChatGPT/Claude/Gemini
consumer apps, external API clients, free-plan quotas, or provider billing.

## Relay contract
- `GET /v1/ai-usage?days=1|7|30|90|365` returns `period_days`,
  `tracking_since`, `timezone=Asia/Tokyo`, `totals`, `models`, `daily`.
- `POST /v1/ai-usage/rates` accepts `profile_id`, `model`,
  `input_usd_per_million` and `output_usd_per_million`; both null clears
  prices. Units are USD per **one million input or output tokens**.
- `GET /v1/ai-usage/export?days=...` exports aggregate CSV by JST date and
  model (not raw conversation or per-request messages). Spreadsheet formulas
  in user-provided display names are escaped.
- All endpoints require the existing `RELAY_CLIENT_TOKEN` Bearer header.
  No new public API, provider credentials, or Android permission.
- All existing `/v1/chat`, `/v1/ai-profiles` and legacy environment-backed
  provider APIs remain backwards compatible.

## Usage and data model
- New SQLite `ai_usage` and `ai_usage_rates` tables are created with
  `CREATE TABLE IF NOT EXISTS`; existing SQLite data, Room chat and
  per-channel AI assignments are unchanged.
- The database logs **only** provider profile ID, display name, provider
  kind, configured model, timestamp, success/error status, token counts if
  provided, latency and a coarse error category. No conversation text,
  generated answers, request/response bodies, API keys or user identity.
- Token usage: OpenAI Responses (`usage.input_tokens/output_tokens`),
  Anthropic Messages (same), compatible Chat Completions
  (`usage.prompt_tokens/completion_tokens`), Dify
  (`metadata.usage.prompt_tokens/completion_tokens`). Missing token counts
  remain NULL and the dashboard highlights unknown usage.
- On failed HTTP responses and provider network errors, count an error
  request without assuming zero tokens. The ledger remains best-effort so
  a database write error does not break chat functionality.
- Recorded usage remains available after removing an AI profile. Rates are
  tracked by (profile ID, model ID). A renamed profile uses the latest
  display name for its currently selected model.
- Daily groupings and lookback periods are evaluated in JST (+09:00).
- Estimates only work if **both** user-provided rates exist and token
  counts are present for every call in that model grouping. Otherwise,
  `estimated_usd` is null. The partial priced sum is displayed separately.
  **Never** misrepresent an unknown charge as zero or an estimated charge
  as the actual bill. Changing rates recalculates earlier usage using
  the new rate, so compare with provider console for accurate billing.

## Deployment
1. Back up Relay SQLite, and preserve existing `RELAY_AI_ENCRYPTION_KEY`.
2. Install the matching Relay image or update `relay/`; new tables are
   created automatically on startup.
3. Upgrade the signed Android APK without uninstalling it.
4. Open 設定 → AIモデルサマリー and tap 更新; select a period.
5. For an estimate, open 単価設定 per model and enter current published
   rates (USD per million tokens); 0 is allowed for confirmed free models.
6. Export aggregate CSV from the document picker, if desired.

## Verification
- Relay tests: authorization, multiple provider usage formats, missing usage,
  provider errors, price edits, deletion preserving history, CSV escaping and
  backwards-compatible database migration.
- Android: unit tests for price validation and allowed date ranges;
  CI compiles debug APK and checks manifest permissions.
- Manual tests remain necessary on Fold cover/expanded displays, IME open,
  local mode without Relay, old Relay with 404, rate editing, export document
  picker and upgrade from prior signed APK.
