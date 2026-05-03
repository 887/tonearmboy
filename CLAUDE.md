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
- **Tests, UI:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp), Claude-driven over ADB. **Current target: headless AVD `medium_phone`** (Android 16 / API 36, RSS ~3.2 GB), started without window/audio/snapshot. Phone via wifi-adb is the long-term home once notification + lock-screen behaviour matters; Waydroid was declined (would need root). The *full graphical* AVD (with window) is what's RAM-prohibitive — headless lands around 3 GB resident, which is fine on this user's 125 GB box.

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

The CLI bundles its own JDK 21 at `~/.android/cli/bundles/<hash>/jre/`. **Caveat:** the bundled JRE is *minimized* — it's missing modules including `java.rmi`, which Gradle 9.1's Kotlin DSL classpath fingerprinter loads. Direct `./gradlew` invocations against the bundled JRE will fail at configuration time with `java.lang.NoClassDefFoundError: java/rmi/Remote`. For direct Gradle calls, export `JAVA_HOME` to a full system JDK 17+ instead — for example `/usr/lib/jvm/java-26-openjdk` on this user's machine. Going through `android run` / other `android` subcommands is fine and uses the bundled toolchain internally.

Practical rule of thumb:
- `android run --apks=…` → just works.
- `./gradlew assembleDebug` → `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug` (or equivalent system JDK 17+ path).

**Worktree caveat:** Gradle reads the SDK path from `local.properties`, which is gitignored. Worktrees created off `main` for subagents start without a `local.properties`, so direct `./gradlew` calls there will fail with `SDK location not found` unless `ANDROID_HOME` is exported (or `local.properties` is generated locally in the worktree). Always export `ANDROID_HOME=$HOME/Android/Sdk` alongside `JAVA_HOME` when invoking Gradle directly in an agent worktree.

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

### UI changes are verified on the running AVD

Any change that touches Compose UI (layout, composable structure, navigation, theming, anything visible) MUST be verified by installing the rebuilt debug APK on the running headless AVD (`emulator-5554`) and inspecting the result — Robolectric unit tests do not catch real-device layout bugs (overflow, clipping, off-screen widgets, rail/scroll behaviour under the now-playing bar, etc.).

Canonical loop:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.eight87.tonearm/.MainActivity
adb -s emulator-5554 exec-out screencap -p > /tmp/tonearm.png   # then Read the PNG
```

Prefer `mobile-mcp` tools when they're loaded in the session (they give the accessibility tree + tap-by-label, much more precise than coordinate input). When mobile-mcp isn't available, fall back to `adb exec-out screencap -p` + visual inspection of the PNG via the Read tool — it's lower-resolution evidence than the a11y tree but enough to confirm widget presence, position, and overflow behaviour.

Do not report a UI task as done on the strength of unit tests + a successful build alone.

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

## Design principles — SOLID, applied to Kotlin + Compose

The codebase follows SOLID where it earns its keep. Kotlin + Compose change *how* the principles cash out (top-level functions instead of `interface ServiceImpl`, sealed types instead of Visitor, `Flow<T>` instead of Observer wiring), but the underlying tests still apply. **When introducing a new file or refactoring an existing one, sanity-check it against these five questions.** When in doubt, prefer the principle over the shortcut.

- **S — Single Responsibility.** A type / file / composable should have *one reason to change*. If you can describe what a class does without "and", "also", or "plus", you're probably fine. If a single file is editable for three independent reasons (e.g. *tab rendering* + *filter persistence* + *navigation routing*), split it. Soft heuristic: anything past ~500 LOC of non-trivial Kotlin deserves a second look; past ~800 LOC almost always needs splitting.
- **O — Open/Closed.** Prefer adding a new sealed-class case / new strategy implementation over modifying an existing `when`/`if` chain that already covers the abstraction. Sealed types + exhaustive `when` are the Kotlin-native way to express "open for extension". *Caveat:* don't pre-build extension points for cases that don't exist yet — closed-by-default, opened only when a second variant arrives.
- **L — Liskov Substitution.** Subtypes (or sealed-type variants) must honour the contract of the parent. A `FilterCondition.HasAlbumArt` that throws on `matches()` for a malformed track would break this — every variant must satisfy `matches: Track -> Boolean` totally. In Compose, this also means: if a composable promises to render in a `Modifier.fillMaxWidth()` parent, every implementation should.
- **I — Interface Segregation.** Don't pass a fat type when a narrow one would do. If a screen needs only `observeTracks()` and `tracksMatching()`, take a `TrackSource` (two methods) — not the whole `LibraryRepository` (40+ methods). In Compose this often manifests as: don't pass a god-state object down five levels; pass the three fields the leaf actually reads.
- **D — Dependency Inversion.** High-level modules (UI, playback orchestration) depend on abstractions, not concrete classes. Concretely: ViewModels / composables take repository *interfaces* or function-typed parameters; concrete Room DAOs / SAF wrappers live behind those interfaces. The `AppGraph` is the composition root — it's the *only* place that knows the concrete types.

These are evaluation criteria, not religion — small ad-hoc helpers don't need their own interface, and one-off composables don't need to be split for principle's sake. But anything load-bearing (repositories, the playback controller, navigation, settings storage) should pass all five.

Refactor plan: see [`docs/plans/refactor-solid.md`](docs/plans/refactor-solid.md) for the running list of SOLID wins identified in audit passes — work them in order of declared priority unless the user picks otherwise.

## Plan file

The phased build plan lives at [`docs/plans/main.md`](docs/plans/main.md), per the user's global CLAUDE.md rule (numbered phases, sub-step checkboxes).

When working on a phase:

- Tick its sub-steps (`- [x]`) in the same commit that lands the work.
- Add `shipped in commit <id>` to the phase header when *all* its sub-steps are ticked.
- Mark the whole plan `## Status: ✅ DONE` once every phase is ticked.
- If a phase header has no sub-step checkboxes, *write them first*. No vibes-based progress.

## Editorial — user-facing copy

The user follows Paul Graham's *Keep Your Identity Small*. App copy (settings descriptions, error messages, About text) should be plain, factual, useful. No "vibes" copy, no personal opinions, no humor that pins identity.

## Release workflow

The user's intended pattern: **vibing from their phone with the Claude app**,
they tell Claude "ship a new build of tonearm." Claude opens a session against
this repo on the dev machine and runs the local build. The user then pulls the
APK via [Obtainium](https://github.com/ImranR98/Obtainium) on their phone,
which auto-detects the new GitHub Release.

**Local build is the primary path. Zero CI minutes by default.**

Canonical commands:

```bash
# Full one-shot: build + push to GH Releases + install on connected device
scripts/build-release-apk.sh --gh-release --install

# Just publish to GH Releases (Obtainium pulls from there)
scripts/build-release-apk.sh --gh-release

# Local APK only, no upload, no install
scripts/build-release-apk.sh
```

What `--gh-release` does:

1. Builds `release/tonearm-<version>-<sha7>.apk` (debug-signed by default).
2. Generates release notes from `git log <prev-tag>..HEAD` plus a
   "Verify build" table containing the commit hash and APK SHA-256.
3. Creates the GitHub Release `v<version>-<sha7>` with the APK attached.
4. Pushes the local annotated tag to `origin`.

The `.github/workflows/release.yml` fallback is **tag-only and self-disabling**:
it triggers when a `v*` tag is pushed, then queries the matching release; if
an APK is already attached (which is true after the local script ran), it
exits 0 without rebuilding. Saves CI minutes by default; only runs when a tag
shows up without a matching APK (e.g. tag pushed from the GH web UI).

When a phase asks for a release, the happy path is `--gh-release --install`
against the connected AVD / wifi-adb phone.

## Subagent dispatching

Subagents working on this repo run in worktrees. Each agent prompt must:

- name the phase + sub-steps it owns
- be told to tick checkboxes and add the commit ID to the phase header as it lands work
- be told to keep the work scoped to its phase (no opportunistic refactors of unrelated code)
- be told to never modify `~/.claude/` files (those are not under this repo)
- be told to consult `android docs search <query>` before hitting general web search for Android API questions
- be told to consult the `android-skills` MCP for any pattern Google has codified (Compose migration, Navigation 3, edge-to-edge, etc.)
