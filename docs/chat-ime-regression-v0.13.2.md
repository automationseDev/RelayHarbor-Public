# Chat keyboard / IME regression check (v0.13.2)

## Problem

Android's `adjustResize` is already present on MainActivity, but the nested
Jetpack Compose chat Scaffold did not apply the software keyboard (IME) inset.
On devices with edge-to-edge layouts, the keyboard could overlap the AI
selector, text input and send button.

## Change

- Apply `Modifier.imePadding()` to the chat Scaffold so its bottomBar and
  message list resize with the system keyboard.
- On the outer Scaffold content wrapper, use
  `Modifier.consumeWindowInsets(innerPadding)` after applying `padding`.
  This prevents the IME modifier downstream from counting the same system bar
  space twice. The same change supports both single-pane and Fold two-pane chat.
- Keep existing `android:windowSoftInputMode="adjustResize"`.
- No database changes, new permissions, or modifications to draft persistence.

## Manual verification matrix

1. **Fold cover screen, portrait**: open a channel, tap the composer, and
   check that AI selector, text box, send button and typed characters remain
   above Gboard/Samsung Keyboard throughout the opening animation.
2. **Fold expanded, two panes**: select a channel from the left and focus the
   composer on the right; verify that the right composer is visible without
   overlap, and the channel list remains responsive.
3. **Thread replies**: open a thread, type a long (4+ line) reply, and verify
   the reply box remains visible and scrolls internally; send it and verify
   the draft is cleared as before.
4. **History**: with the keyboard open, scroll older messages in the remaining
   list area; closing the IME restores the original height without jumping
   to another channel.
5. **Navigation modes**: repeat with gesture navigation and three-button
   navigation; verify there is not a doubled empty band between keyboard
   and composer.
6. **Rotation and physical keyboard**: rotate the device and try hardware
   keyboard input (no software IME); confirm the composer is not pushed
   upward when no IME is displayed.
7. **No regressions**: confirm HOME mode, channel search and AI selection
   still work, app data persists after updating over the previous signed APK.

CI builds the Android debug APK and runs local tests; this manual matrix must
be completed on a physical Fold before asserting actual device verification.
