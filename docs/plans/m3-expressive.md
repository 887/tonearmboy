# tonearmboy — Material 3 Expressive (M3E) migration

## Status: IN PROGRESS — Phases A–C shipped, splash retuned, all-views sweep next

## Findings from the first ship (read this before continuing)

Five gotchas surfaced when actually wiring this up. Capture for
future-me and any sub-agents picking up Phase E.

1. **`material3:1.4.0` keeps `MaterialExpressiveTheme` /
   `expressive*ColorScheme` `internal`.** The Compose BOM
   `2026.03.01` resolves to `material3:1.4.0` but you can't actually
   call the expressive APIs from there — the Kotlin metadata marks
   them `internal`, even though the JVM bytecode is public. We
   override the BOM in `gradle/libs.versions.toml` with
   `composeMaterial3 = "1.5.0-alpha18"`, the alpha that promoted the
   APIs to public. `expressiveDarkColorScheme()` does NOT exist in
   1.5.0-alpha18 — only the `light` factory ships. Dark mode stays
   on `darkColorScheme(...)` and inherits the surface-tier ladder.
   Drop the override once 1.5.0 stable lands (Phase F).
2. **`surfaceContainer` is too quiet on AMOLED-leaning dark
   palettes.** Phase B started with `containerColor =
   surfaceContainer` and it was barely a step above `surface`. The
   ship uses `surfaceContainerHigh`. Light mode with
   `expressiveLightColorScheme()` reads better at `surfaceContainer`,
   so revisit if/when light mode gets a polish pass.
3. **Auto-derive accent from `id` at the `SettingsRow` layer, not
   per call site.** Initially we passed `accent = accentFor(entry.id)`
   from every `SettingsRowBinding.Render` impl + from
   `SettingsScreen.kt`. That left direct `SettingsRow(...)` callers
   like `AboutScreen` / `LicensesScreen` (which don't go through
   bindings) monochrome. The fix is one-liner: `SettingsRow` falls
   back to `accentFor(id)` internally when caller passes
   `accent = null` and a non-null `id`. Every direct caller now
   gets the avatar without changes.
4. **Android 12+ splash icon is hard circle-clipped, period.** The
   layer-list `android:windowBackground` workaround does NOT work —
   the system splash paints `windowSplashScreenBackground` over the
   whole window during launch, covering anything you set on
   `windowBackground`. The working approach: ship a dedicated splash
   mipmap (`ic_launcher_splash.png`) where the design is shrunk so
   it inscribes inside the system circle. **70.7 % (1/√2) is too
   tight** — corners still graze the mask. **60 %** gave proper
   headroom on the user's device. Set
   `windowSplashScreenIconBackgroundColor = launcher_background` so
   the bigger 240-dp icon area kicks in.
5. **Album-art tint in `Theme.kt` blends `surface`/`surfaceVariant`/
   `background` but NOT the `surfaceContainer*` ladder.** That's
   why the library list / detail cards look uniformly tinted with
   the album palette while the page surface drifts. Long-term we
   should blend the whole ladder; for the ship it was acceptable.
   Phase E or a follow-up.

## Scope expansion: "all the views"

User feedback after seeing About + Licenses still monochrome:
> "we want all the views updated/modernized"

Phase E now sweeps the WHOLE app, not just Settings. The
auto-accent change (finding 3) gets us most of the settings world
for free; the rest of the app (Library, Now Playing, sheets,
dialogs) needs explicit work.

## Why

The user side-by-side'd two screenshots:

1. Android 16 system **Settings** — vibrant circular coloured row icons
   (sky-blue Network, purple Apps, pink Sound, magenta Modes, orange
   Display, peach Wallpaper), rounded grouped cards on a slightly
   lighter elevated surface than the page background. "Happy", colourful,
   expressive.
2. tonearmboy **Settings** — same card grouping shape but flat, near-
   black on near-black, monochrome thin-outlined glyph icons. Reads as
   muted and low-contrast.

The user's hypothesis was right: the system Settings UI is **Material
3 Expressive (M3E)**, the next iteration of Material 3 that Google
rolled out across the system UI starting Android 16 / Android 16 QPR1
(Sept 2025) and is still expanding through QPR2.

We're on baseline Material 3. Three concrete gaps:

- **Surface-tier collapse** — page and cards both pull from
  `colorScheme.surface`, so the cards don't pop. M3 ships the full
  ladder `surfaceContainerLowest` < `surfaceContainerLow` <
  `surfaceContainer` < `surfaceContainerHigh` < `surfaceContainerHighest`
  and we use only the bottom rung.
- **Outlined glyph icons** with `tint = onSurface` on a transparent
  background, instead of filled glyphs sitting inside a coloured
  circle avatar.
- **No `MaterialExpressiveTheme` opt-in** — without it we get the
  pre-M3E motion / typography / shape defaults.

## Constraints (locked)

- **Follow upstream M3 token names**, don't invent custom palette
  names. Use `surfaceContainer*` not `cardBackground`, etc.
- **Per-category accent** for the row icon avatars must come from a
  small `CategoryAccent` data class in the theme (M3 only ships three
  container pairs — `primaryContainer`, `secondaryContainer`,
  `tertiaryContainer` — and the system Settings spreads ~6 hues).
  Hand-pick the six (or so) accent pairs in dark + light variants;
  don't try to drive them off `dynamicDarkColorScheme()` since the
  user's wallpaper-driven palette would steamroll the per-category
  intent.
- **Compose `material3` artifact:** stable line is `1.4.0` (Sept 2025);
  expressive APIs require `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
  there. `1.5.0-alpha18` (Apr 2026) promotes them to stable. We pin
  **1.4.0 + opt-in** for now, drop the opt-in once 1.5.0 stable ships.
- **Filled icon set** — `androidx.compose.material:material-icons-extended`
  (no longer transitive in `material3:1.4.0+`, must be added
  explicitly). Sweep `Icons.Outlined.*` → `Icons.Filled.*` at the
  settings-row layer only; don't touch icon usage inside the player /
  library which already reads filled.

## Phase A — bump dependencies + opt-in scaffolding

- [ ] **A.1** Bump `androidx.compose.material3:material3` to `1.4.0`
  in `gradle/libs.versions.toml` (and bump the version-catalog entry
  if pinned there).
- [ ] **A.2** Add explicit dep on
  `androidx.compose.material:material-icons-extended:<version>`
  matching the existing icons-core line.
- [ ] **A.3** `./gradlew :app:licenseeAndroidDebug` to confirm SPDX
  is still in the allowlist after the bump (per `CLAUDE.md` license
  workflow).
- [ ] **A.4** Add a top-level `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
  to the theme file so the rest of the codebase doesn't have to
  carry the annotation.
- [ ] **A.5** Replace the root `MaterialTheme(...)` wrapper in
  `TonearmboyTheme.kt` (or wherever the app theme entry lives) with
  `MaterialExpressiveTheme(colorScheme, shapes, typography, motionScheme)`.
  Keep the existing `colorScheme` for now — Phase B introduces the
  expressive seeds.

## Phase B — colour scheme: expressive seeds + surface tiers

- [ ] **B.1** Switch `darkColorScheme()` calls to
  `expressiveDarkColorScheme()` (and same for light). The expressive
  seed produces the brighter container pairs M3E expects.
- [ ] **B.2** Audit every `colorScheme.surface` / `colorScheme.background`
  call site under `ui/`. Goal: page = `surface`, card = `surfaceContainer`.
  Each callsite either stays as `surface` (page-level) or moves to
  `surfaceContainer` (card-level). On AMOLED-leaning dark themes try
  `surfaceContainerHigh` if `surfaceContainer` doesn't separate enough.
- [ ] **B.3** Audit `Card`, `Surface`, and `libraryDetailCard()` /
  `libraryListCard()` modifiers. Each card grouping should:
    - have `containerColor = MaterialTheme.colorScheme.surfaceContainer`
    - have `defaultElevation = 0.dp` (system Settings reads as flat
      against the page; the surface-tier token does the lift, not a
      shadow)
    - use `RoundedCornerShape(28.dp)` or `MaterialTheme.shapes.extraLarge`
      to match the M3E "extra-large" group shape
- [ ] **B.4** `app/src/test/.../theme/SurfaceTierContractTest.kt` —
  unit test asserting that the dark colour scheme's `surface` and
  `surfaceContainer` resolve to two distinguishable RGB values
  (delta-E threshold). Cheap regression guard against an accidental
  re-flatten.

## Phase C — `SettingsCategoryIcon` composable

- [ ] **C.1** Add `data class CategoryAccent(val container: Color, val onContainer: Color)`
  alongside the theme.
- [ ] **C.2** Define ~6 `CategoryAccent` values in the dark + light
  flavours: at minimum `Appearance` (peach), `Behaviour` (sky blue),
  `Library` (purple), `Audio` (pink), `Now Playing` (orange),
  `About` (green). Hand-pick HSLs; aim for chroma ~80, lightness
  matching M3E's `*Container` token feel.
- [ ] **C.3** New composable
  `SettingsCategoryIcon(icon: ImageVector, accent: CategoryAccent,
  contentDescription: String?)` — `Box` with `Modifier.size(40.dp).
  clip(CircleShape).background(accent.container)` and an
  `Icon(modifier = Modifier.size(24.dp).align(Alignment.Center),
  tint = accent.onContainer)`.
- [ ] **C.4** Convert the Settings catalog's row-rendering code so
  group leads / category rows render through `SettingsCategoryIcon`.
  Outline → filled icon swap happens here too — the avatar reads
  weak with thin glyphs.
- [ ] **C.5** AVD smoke: open Settings root, confirm the six
  category rows now show coloured circles with filled glyphs against
  a card that's visibly lighter than the page.

## Phase D — typography + spacing audit

- [ ] **D.1** Settings row title: `MaterialTheme.typography.titleMedium`.
- [ ] **D.2** Settings row subtitle: `MaterialTheme.typography.bodyMedium`
  with `color = MaterialTheme.colorScheme.onSurfaceVariant`.
- [ ] **D.3** Within-card row stacking: `Column(verticalArrangement
  = Arrangement.spacedBy(2.dp))` inside each card; drop `HorizontalDivider`
  between settings rows (M3E goes divider-less, surface-tier and the
  spacedBy gap do the visual separation).
- [ ] **D.4** Row inner padding: `padding(horizontal = 16.dp,
  vertical = 12.dp)` to match the system metric.
- [ ] **D.5** AVD smoke: scroll the Settings root and the Look-and-Feel
  / Personalize / Content / Audio leaves; nothing overflows, nothing
  clips, the typography reads identically to system Settings on the
  same density.

## Phase E — sweep the rest of the UI

Settings was the trigger but the full app should land on M3E. Sweep
in priority order; each sub-step ships its own commit.

- [ ] **E.1** Library tab chrome (top app bar, tab strip, FAB) —
  promote to `surfaceContainer`-on-`surface` if the dispatcher is
  currently flattening.
- [ ] **E.2** Detail screens (Album / Artist / Genre / Playlist) —
  card colour audit, divider removal where the surface-tier gap is
  doing the job.
- [ ] **E.3** Now Playing screen — already heavily art-dominated;
  sweep button shapes only (M3E pulls towards the `extraLarge`
  rounded shape for primary actions).
- [ ] **E.4** Sheets and dialogs — `ModalBottomSheet` /
  `AlertDialog` defaults already pull from M3 tokens; verify the
  new expressive container colours flow through and look right
  against the new page background.

## Phase F — opt-in cleanup (deferred)

- [ ] **F.1** When `androidx.compose.material3:material3:1.5.0`
  ships stable, bump the dep and remove the
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` annotation
  added in A.4. Track upstream — alpha18 already promoted the API,
  so 1.5.0 stable is a quarter or two out at most.

## References

- developer.android.com/develop/ui/compose/designsystems/material3 —
  official M3E + Android 16 visual style; surface roles list
- developer.android.com/jetpack/androidx/releases/compose-material3 —
  release notes (1.4.0 stable Sept 2025; 1.5.0-alpha18 Apr 2026
  promotes expressive APIs)
- 9to5google.com/2025/09/03/android-16-qpr1-pixel/ — confirms
  Android 16 QPR1 shipped the M3E redesign for Pixel
- android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html
  — Google's M3E reference app (source: github.com/android/androidify)
- m3.material.io/develop/android/jetpack-compose — M3 Compose landing

## Effort + risk

- **Phase A:** S (½ day). Dep bump, single annotation site.
- **Phase B:** M (1 day). Surface-tier audit is mechanical but
  touches every card site.
- **Phase C:** M (1 day). Six accent pairs + one composable +
  catalog wiring.
- **Phase D:** S (½ day). Token replacement.
- **Phase E:** M–L (1–2 days). Depends on how aggressive the sweep
  goes; can be split into per-screen commits.
- **Phase F:** XS — drops one annotation.

Total: ~4–5 days of focused work to land Settings + an app-wide
sweep. Phases A–D are the user's direct ask; E is the natural
follow-through.

## Risk / unknowns

- **Wallpaper-driven Material You vs hand-picked accents:** the
  Phase C plan picks accents by hand to avoid the user's wallpaper
  palette overriding category intent (a green wallpaper would tint
  the Audio "pink" accent off-pink, defeating the colour-coding).
  Document that decision in the theme file.
- **Light-mode pass:** the screenshots are dark-mode only. Once
  Phase C lands, AVD-smoke light mode too — `expressiveLightColorScheme()`
  + the same accent data class needs validating against the lighter
  background.
- **`Icons.Outlined.*` → `Icons.Filled.*` sweep:** scope this to
  Settings-row leading icons only. The library / now-playing icons
  already use the filled set; don't churn them.
