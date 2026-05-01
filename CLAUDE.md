# tonearm — Claude instructions

Modern Android music player. Kotlin + Jetpack Compose + Media3 + Room. Built entirely from the CLI, no Android Studio required, no QEMU emulator.

## Architectural decisions (locked)

- **Language:** Kotlin only. No Java.
- **UI:** Jetpack Compose. No Android Views.
- **Audio:** [androidx.media3](https://developer.android.com/media/media3) (ExoPlayer + MediaSession + MediaSessionService). This matches Auxio's stack.
- **Data:** Room for cached MediaStore metadata.
- **Build:** Gradle CLI (`./gradlew`). The repo includes a Gradle wrapper. Android SDK installed via `cmdline-tools` / `sdkmanager`. **Do not introduce Android Studio project files** (`.idea/`, `*.iml`).
- **Tests, unit:** Robolectric. JVM-only. No device required.
- **Tests, UI:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp), Claude-driven over ADB. Target is the user's phone via wifi-adb, or [Waydroid](https://waydro.id/) on Linux. **Never use a full QEMU emulator (AVD) — RAM-prohibitive on this user's setup.**

## Test loop

The user runs the MCP server registration once per machine:

```bash
claude mcp add mobile -- npx -y @mobilenext/mobile-mcp@latest
```

Once registered, an in-Claude-Code session can call mobile-mcp tools to:

- list connected devices (`adb devices` equivalent)
- install an APK
- launch the app
- read the accessibility tree (the screen state, the way Playwright reads the DOM)
- tap by label / coordinates
- assert UI state

Build → install → drive flow:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# then mobile-mcp tools take over for UI interaction
```

For raw ADB without mobile-mcp:

```bash
adb logcat -s tonearm:* AudioFocus:* MediaSession:*
adb shell am start -n com.eight87.tonearm/.MainActivity
```

## File conventions

- Single-module to start. Split into `:core` / `:data` / `:ui` only when the single-module size warrants it; do not premature-modularize.
- Package root: `com.eight87.tonearm`.
- Composable functions: PascalCase, no `@Composable` on private helpers unless they take a Modifier.
- ViewModels: one per screen, talk to the data layer via repository interfaces.
- No DI framework in v1 (Hilt/Koin) — pass dependencies as constructor params. Add DI later if/when the manual wiring hurts.
- No reflection-based JSON. Use `kotlinx.serialization` if any serialization is needed.

## Plan file

The phased build plan lives at [`docs/plans/main.md`](docs/plans/main.md), per the user's global CLAUDE.md rule (numbered phases A–G, sub-step checkboxes A.1, A.2, …).

When working on a phase:

- Tick its sub-steps (`- [x]`) in the same commit that lands the work.
- Add `shipped in commit <id>` to the phase header when *all* its sub-steps are ticked.
- Mark the whole plan `## Status: ✅ DONE` once every phase is ticked.
- If a phase header has no sub-step checkboxes, *write them first*. No vibes-based progress.

## Editorial — user-facing copy

The user follows Paul Graham's *Keep Your Identity Small*. App copy (settings descriptions, error messages, About text) should be plain, factual, useful. No "vibes" copy, no personal opinions, no humor that pins identity.

## Subagent dispatching

Subagents working on this repo run in worktrees. Each agent prompt must:

- name the phase + sub-steps it owns
- be told to tick checkboxes and add the commit ID to the phase header as it lands work
- be told to keep the work scoped to its phase (no opportunistic refactors of unrelated code)
- be told to never modify `~/.claude/` files (those are not under this repo)
