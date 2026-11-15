# RelayHarbor v0.11.0 — Slack-inspired conversations

## Implemented
- Root-message threads: tap a message or **返信** to view its replies; send device-local responses. Replies do not invoke AI or external Webhooks.
- Local reactions (👍 ❤️ 🎉 👀 ✅ 🔥), toggle by long press; preserved across restarts.
- Long-press actions: reply, react, copy, Android share, pin/unpin incoming messages, edit/delete own text with confirmation.
- Root messages with replies cannot be deleted. Editing and deleting are local only.
- Channel detail dialog: topic, description, message count and pinned-message preview. Topic/description are private on-device display metadata and do not rename the routing channel.
- v4→v5 Room migration with nullable indexed parentMessageId and cascading message_reactions table. Older messages remain roots.

## Safety and compatibility
- No remote notification content upload, new dangerous permissions, arbitrary Webhook actions, or AI calls from thread replies.
- Keep net.automationse.relayharbor, original production signer, and non-destructive migrations.
- Version 0.11.0 / versionCode 42.

## Checks
- JVM unit tests: root/reply filtering and reaction validation.
- Android CI + Relay tests + signed-release workflow.
- Verify on device: upgrade from v0.10.3 with history, existing channel routing, threads and reaction persistence, delete/edit restrictions, pinned list, Fold cover/open, Android Back and sharing.
