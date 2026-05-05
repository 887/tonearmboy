# tonearmboy — translations plan

## Status: 🟡 IN PROGRESS — Phases T.A through T.D done; T.E.1 (German) drafted at 100% coverage, awaiting per-entry editorial review. Phase stays open for future locales (T.E.2, T.E.3, …).

## The model

**Translations are produced by the user + Claude, per-language, in dedicated sessions.** That's the canonical workflow, not a fallback. Community PRs are accepted if they show up, but nothing in the pipeline assumes or requires them — no contributor onboarding doc, no PR template addendum, no "welcome mat" infrastructure.

Per-language session shape:
1. User picks a target locale.
2. Claude reads the canonical `values/strings.xml` and the editorial brief from CLAUDE.md (plain / factual / useful — no vibes copy).
3. Claude drafts `values-<locale>/strings.xml` with every key translated.
4. User reviews per-entry; corrects what's off; commits what's signed off.
5. README progress table regenerates on the next release.

That's the whole loop. No external service, no contributor coordination, no "wait for review pair" — just user + Claude.

## Constraints (locked)

- **No third-party translation service.** No Crowdin, no Lokalise, no Weblate (hosted or self-hosted). Translations live as plain XML files in the repo.
- **No new build dependency.** The whole pipeline is Android's built-in `values-<locale>/strings.xml` mechanism plus a small POSIX shell script for the README progress table. No Gradle plugins, no SDKs.
- **Zero CI minutes by default.** Translation-progress regeneration runs locally inside `scripts/build-release-apk.sh`, the same way the APK + release does. The README table updates as part of the next release commit; no `on: push` workflow.
- **English is canonical.** `app/src/main/res/values/strings.xml` is the source of truth. Locale variants are partial overrides; missing keys fall back to English at runtime (Android default behaviour).
- **Editorial discipline carries over.** Per CLAUDE.md: app copy stays plain, factual, useful. Translations match that register.

## Cross-cutting themes (the audit in one breath)

- **357 hard-coded user-facing strings** scattered across `ui/**` Compose source — settings labels, picker titles, toast/snackbar copy, empty-state messages, dialog buttons, accessibility content descriptions. None of them are resource-backed today; the `app_name` row is the only entry in `values/strings.xml`.
- Without an extraction pass, no translation is possible — Android only switches what's behind `stringResource(R.string.…)` or `LocalContext.current.getString(R.string.…)`. So Phase T.A is foundational and unavoidable.
- testTags, log tags, internal sentinels (`"_hidden_"`), debug-only strings, and code constants (`"%.1f"` format strings) are NOT user-facing and stay hard-coded.
- Compose `stringResource(R.string.foo)` is a Composable function. Some current sites are not in a `@Composable` scope (e.g. inside `remember { ... }` lambdas, snackbar messages launched from a coroutine). Those need a Context handle (`LocalContext.current.getString(...)`), or the lookup hoisted to a composable parent.

## Phase T.A — extract hard-coded strings to `values/strings.xml`

**Why:** Foundational. Every later phase assumes user-facing copy already lives in resources.

**How to apply:** Mechanical pass file by file. Stable naming scheme so future audits stay grep-able.

- [x] **T.A.1** Naming scheme: `<surface>_<role>` lowercase snake. Documented in a leading XML comment in `values/strings.xml`.
- [x] **T.A.2** Settings sub-pages. ~120 strings. *(shipped — 198 entries in `values/strings_settings.xml`.)*
- [x] **T.A.3** Library tabs + detail screens. ~100 strings. *(shipped — 123 strings + 9 plurals in `values/strings_library.xml`. `TabSpec.toTile` now takes a `Resources`. `ConditionUi.label` / `addSubtitle` are `@StringRes`; `summary()` takes a `Context`.)*
- [x] **T.A.4** NowPlaying + MiniPlayer + queue. ~50 strings. *(shipped — ~35 strings in `values/strings_playing.xml`. SleepTimerDialog handled by T.A.2.)*
- [x] **T.A.5** Permissions + first-launch + system messages. ~40 strings. *(shipped — 24 entries in `values/strings_permission.xml`. `library_scan_*` rescan/refresh snackbars in `ui/nav/RouteScopeFactory.kt` deferred to T.A.7.)*
- [x] **T.A.6** Search + playlist picker + collision dialog. ~30 strings. *(shipped — ~50 strings + 6 plurals in `values/strings_playlist.xml`. Surfaces: `SearchScreen`, `SettingsSearchScreen`, `PlaylistPickerSheet`, `PlaylistsTilesScreen`, `PlaylistDialogHost`, `PlaylistImportDialog`, `AddToPlaylistController`, `PlaylistBackupController`.)*
- [x] **T.A.7** Audit pass: grep for any remaining `Text("…")` or `contentDescription = "…"` in `ui/**` and confirm each is either resource-backed or genuinely internal. *(swept the residual snackbars in `RouteScopeFactory.kt` + `LibraryRoutes.kt` into `library_scan_*` / `library_added_to_queue` / `library_playlist_renamed` / `library_playlist_deleted` / `library_playlist_cover_*` / `library_playlist_updated` / `library_coming_soon`. `handleDetailTrackAction` gained a `Context` param so its snackbar can resolve the format string.)*
- [x] **T.A.8** Verify: Robolectric tests green; AVD smoke confirms no string visibly broke.
- [x] **T.A.9** Ship + tick.

**Effort:** L (1.5 days). **Risk:** low — mechanical, but tedious; tests catch most regressions. **Sequence note:** Best done AFTER R.D's `LibraryScreen` split because the resulting smaller files are easier to extract from. T.A.4 + T.A.5 can land before R.D since they're in `ui/playing/` and `ui/permission/`.

---

## Phase T.B — locale infrastructure

**Why:** Set the file-layout contract before any locale lands.

- [x] **T.B.1** Confirm `<application>` doesn't pin a locale (default behaviour follows system locale). Already the case; no-op verification.
- [x] **T.B.2** Add `<resources xmlns:tools="http://schemas.android.com/tools" tools:locale="en">` to `values/strings.xml` so Android Studio / lint treats English as canonical.
- [x] **T.B.3** Add a short paragraph to CLAUDE.md (NOT a separate CONTRIBUTING file) covering: how the user + Claude generate a locale, the `values-<locale>/strings.xml` convention, the editorial register (plain / factual / useful), and that missing keys fall back to English. Total: maybe 8 lines. Not a contributor doc — a session-instructions doc for the user's own future Claude sessions.
- [x] **T.B.4** Verify: `:app:assembleDebug` clean; AVD locale switch (`adb shell cmd locale set-app-locales com.eight87.tonearmboy --locales de-DE`) shows English fallback when no `values-de/` exists yet. (`persist.sys.locale` rejected on the AVD without root; per-app locale via `cmd locale` is the documented modern path and works without root.)
- [x] **T.B.5** Ship + tick.

**Effort:** XS (1–2 hours). **Risk:** low.

---

## Phase T.C — translation-progress script + README markers

**Why:** The user wants a visible "X% done in language Y" signal in README without Crowdin/Weblate/server.

- [x] **T.C.1** Write `scripts/translation-progress.sh`: parses every `values/strings*.xml` → set of canonical keys (excluding `translatable="false"` rows); for each `values-<locale>/strings*.xml` parses translated keys; computes `done / total` and the locale's display name; prints a markdown table. Surface XML files (`strings_settings.xml`, `strings_library.xml`, etc.) are globbed so the script handles the per-surface split.
- [x] **T.C.2** Added `<!-- TRANSLATIONS-START -->` / `<!-- TRANSLATIONS-END -->` markers in `README.md` (new "Translations" section). Script's `--update` mode rewrites between markers via `awk` block-replace — idempotent.
- [x] **T.C.3** Wired the script into `scripts/build-release-apk.sh` immediately before the GH-release step: regenerates the README block; release commit picks up the updated table when locales change.
- [x] **T.C.4** Golden tests at `scripts/tests/translation-progress/{empty,partial-de,full-de,no-locale}/`. Run via `scripts/translation-progress.sh --test`. All 4 cases pass.
- [x] **T.C.5** Verified: `--update` is byte-stable on a no-op second run (`diff /tmp/readme-before.md README.md` clean).
- [x] **T.C.6** Ship + tick.

**Effort:** S–M (½–1 day). **Risk:** low (POSIX shell + sed + grep).

---

## Phase T.D — README "Translations" section content

**Why:** The auto-table is the data; this is the surrounding prose.

- [x] **T.D.1** Above the auto-generated table: a 2-sentence paragraph explaining the model (translations are maintainer + Claude per-language, English is canonical, missing keys fall back to English). NOT a "we welcome contributions" pitch.
- [x] **T.D.2** Linkified each language row to its `values-<locale>/` on github so the maintainer can jump straight to "edit this directory" — the script emits `[Language](app/src/main/res/values-<locale>/)`.
- [x] **T.D.3** Ship + tick.

**Effort:** XS (15 min). **Risk:** none.

---

## Phase T.E — produce locales (one session per language)

**Why:** This IS the ship vector. Every supported language lands here, not via outside contributors.

**Standing per-language workflow:**
1. User opens a Claude session in this repo, names the target locale.
2. Claude reads `values/strings.xml` + the CLAUDE.md editorial brief.
3. Claude drafts `values-<locale>/strings.xml` with every translatable key. Keeps the same key order as `values/strings.xml` for easy diff review.
4. Per-entry user review. Anything off → user redirects → Claude revises in place.
5. Commit signed-off entries; leave anything unconfirmed missing (English-fallback is correct behaviour, not a placeholder).
6. Run `scripts/translation-progress.sh` to refresh the README table.
7. AVD smoke: switch locale, walk every screen, watch for layout overflow on long compound words (German specifically — `flowRow` / `wrapContentWidth` may need targeted patches).

Per-locale ticks (extend as new languages land):

- [x] **T.E.1** German (`values-de/`) — user is local, primary review channel. Drafted via 6 parallel sub-agents (one per surface XML), 481/481 keys (100% coverage). Caught + fixed a T.A regression: `GroupRef` was holding hardcoded English label strings; converted to `@StringRes` so Settings root section headers (Darstellung / Verhalten / Bibliothek) resolve through `stringResource`. Per-app locale switch via `cmd locale set-app-locales` confirmed German renders end-to-end. Per-entry editorial review still pending — drafted, not signed off.
- [ ] **T.E.2** Next locale — user picks; same workflow.
- [ ] **T.E.3** Next locale — same workflow.
- [ ] (… one sub-step per locale shipped)

This phase is **never "done"** in the conventional sense — it stays open as long as new locales are added. Tick the parent phase header with the commit range when the user declares a particular set of locales the canonical shipped set; reopen later when adding a new one.

**Per-locale effort:** M (½–1 day, mostly editorial review). **Risk:** low. **Blast radius:** `values-<locale>/` + README.

---

## What this plan deliberately does NOT include

- **No CONTRIBUTING-TRANSLATIONS.md.** Replaced by 8 lines in CLAUDE.md (T.B.3) describing the user-+-Claude workflow.
- **No PR template addendum** for translation contributions.
- **No "welcome mat" copy** in README pitching community translations.
- **No `<!-- needs-translation -->` placeholder convention** — just leave keys missing; Android falls back to English, the progress script counts honestly.
- **No reviewer-pair rule, no language-native verifier requirement** — the user reviews per-entry inside the session.

## Why this is "no cost"

- No new SDK / plugin / dependency in `build.gradle.kts`.
- No external service account, hosted instance, or recurring subscription.
- No CI minutes burned (README table piggybacks on `--gh-release` local build).
- Translation labour is the user reviewing what Claude drafts. No outside coordination overhead.
- Adding a new language is one new file. Removing a language is `git rm -r values-<locale>/`.

## Migration path (kept open, not scheduled)

If at some future point the user decides to open community translations:
- Layout is unchanged — `values-<locale>/strings.xml` is the standard Weblate/Crowdin input format.
- Add a `CONTRIBUTING-TRANSLATIONS.md` then.
- Add a PR template addendum then.
- This plan covers none of that work pre-emptively.
