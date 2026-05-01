# tonearm — Claude instructions

Modern Android music player. Kotlin + Jetpack Compose + Media3 + Room. Built entirely from the CLI, no Android Studio required, no QEMU emulator.

## Architectural decisions (locked)

- **Language:** Kotlin only. No Java.
- **UI:** Jetpack Compose. No Android Views.
- **Audio:** [androidx.media3](https://developer.android.com/media/media3) (ExoPlayer + MediaSession + MediaSessionService). Same stack as Auxio.
- **Data:** Room for cached MediaStore metadata.
- **Build front-end:** [Google's Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps project creation, SDK management, build, install, and run. **Do not introduce Android Studio project files** (`.idea/`, `*.iml`).
- **Build back-end:** Gradle (driven by the Android CLI; the wrapper is committed to the repo).
- **Tests, unit:** Robolectric. JVM-only. No device required.
- **Tests, UI:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp), Claude-driven over ADB. Target is the user's phone via wifi-adb, or [Waydroid](https://waydro.id/) on Linux. **Never use a full QEMU emulator (AVD) — RAM-prohibitive on this user's setup.**

## Required CLIs and MCP servers

These are user-machine prerequisites. The plan tracks each in Phase 0.

### Android CLI

The new (April 2026) `android` command from Google wraps everything we need.

Install (userspace, this user's setup):

```bash
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android
chmod +x ~/.local/bin/android
android --version  # self-bootstraps the runtime on first call
```

The CLI bundles its own JDK 21 at `~/.android/cli/bundles/<hash>/jre/`. The system JDK is irrelevant when going through `android`. If a subagent ever needs to invoke `./gradlew` directly, export `JAVA_HOME` to that path first; otherwise prefer `android run` which handles the toolchain internally.

Useful subcommands:

```bash
android create list                                  # browse project templates
android create --name=tonearm --output=. <template>  # scaffold a new project
android sdk install platforms/android-34 build-tools/34.0.0
android run --apks=app/build/outputs/apk/debug/app-debug.apk
android docs search <query>                          # query the Android Knowledge Base
android docs fetch <kb-url>                          # fetch a specific KB doc
android skills list --long                           # browse official Android skills
android info                                         # show detected SDK + version
```

`android docs search` is **the first place to look** when uncertain about Android APIs. It returns up-to-date guidance from the official Android Knowledge Base — beats grepping web search results, and is on-machine.

### `mobile` MCP server (UI driving)

Registered at **project scope** in `.mcp.json` (committed to the repo) and allowed in `.claude/settings.json` (also committed). When a Claude Code session starts in this repo with `enableAllProjectMcpServers: true` (set in the project settings), the `mcp__mobile__*` tools become available automatically.

To re-register on a fresh checkout if for any reason the project config drops the entry:

```bash
claude mcp add mobile --scope project -- npx -y @mobilenext/mobile-mcp@latest
```

What it gives you: list connected ADB targets, install APKs, launch the app, read the accessibility tree (the screen state, the way Playwright reads the DOM), tap by label / coordinates, assert UI state.

### `android-skills` MCP server (official Android skills)

Registered at **project scope** in `.mcp.json` and allowed in `.claude/settings.json`. Surfaces Google's official Android Skills (Compose migration, Navigation 3, Edge-to-Edge, AGP 9, R8 config, Media3 patterns, etc.) as MCP tools inside Claude Code. **Consult these before hand-rolling any Android-specific pattern** that could be load-bearing on platform conventions.

To re-register on a fresh checkout:

```bash
claude mcp add android-skills --scope project -- npx -y android-skills-mcp
```

### Test target

One of:

- **wifi-adb to the user's phone** (preferred — zero machine RAM cost):
  ```bash
  adb pair <ip>:<pair-port>      # pair once
  adb connect <ip>:<connect-port>
  adb devices                    # confirm
  ```
- **Waydroid** on Linux (LXC container, ~1-2 GB resident, shares host kernel):
  ```bash
  waydroid session start
  adb connect 192.168.240.112:5555  # default Waydroid IP
  ```

## Test loop

```bash
./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/app-debug.apk
# mobile-mcp tools take over for UI interaction
```

Or all-in-one via Android CLI when build + run is the same step.

For raw ADB inspection during dev:

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

The phased build plan lives at [`docs/plans/main.md`](docs/plans/main.md), per the user's global CLAUDE.md rule (numbered phases, sub-step checkboxes).

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
- be told to consult `android docs search <query>` before hitting general web search for Android API questions
- be told to consult the `android-skills` MCP for any pattern Google has codified (Compose migration, Navigation 3, edge-to-edge, etc.)
