# tonearmboy — open-source licenses plan

## Status: ✅ DONE

## Why

The app ships under MIT (`LICENSE` in repo root, generated from a clean-room rewrite to avoid the GPL of comparable players). Every dependency that ships in the APK is **Apache License 2.0** — every `androidx.*` module, the kotlinx ecosystem, Coil 3, the okio + okhttp Coil pulls transitively, Guava-via-`kotlinx-coroutines-guava`. Apache 2.0 §4 requires that downstream binary distributions preserve copyright + NOTICE entries from upstream artifacts. The Android-conventional way to satisfy this is an "Open-source licenses" sub-page: a list of every shipping dep with name, version, license SPDX, and license body.

The licenses surface is also user-friendly: people who pick up the app from F-Droid / Obtainium and look at the About screen want to know what's underneath.

This plan is small. The About surface already exists at `app/src/main/java/com/eight87/tonearmboy/ui/settings/AboutScreen.kt` and ships a "Source" card linking to the GitHub repo and the MIT license. We add a Licenses entry that opens a sub-screen rendering a build-time-generated inventory.

## Approach (locked)

- **Build-time inventory, zero runtime deps.** Use the [`app.cash.licensee`](https://github.com/cashapp/licensee) Gradle plugin. It walks the resolved `releaseRuntimeClasspath` at configuration time and writes a JSON inventory; nothing is added to the APK at runtime. Licensee is Apache 2.0 itself, build-time only, and is the pattern Cash App / Square use.
- **Compose-rendered sub-screen.** A new `LicensesScreen.kt` reads the generated `artifacts.json` from `assets/licenses/` and renders a `LazyColumn` of M3-Expressive cards. Tapping a row reveals the license body. License bodies (Apache-2.0, MIT, EPL-1.0) ship as raw text assets — finite set, three at most.
- **Robolectric-driven catalog test.** Parses the generated JSON, asserts non-empty, asserts every entry has a known SPDX and a backing license-text asset, asserts a known sample of shipping deps is present. Catches accidental dep removal and unknown-license additions.
- **Report-only allowlist in v1.** Licensee can fail the build on disallowed licenses. We declare the allowlist (`Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`) but do not enforce in v1. A second pass can flip enforcement once the inventory has been reviewed once.

## Inventory snapshot (informational — confirmed `2026-05-04` against `gradle/libs.versions.toml` + `app/build.gradle.kts`)

Apache 2.0 unless otherwise marked. **Authoritative source is the Licensee-generated `artifacts.json` once Phase A lands** — this list is for the planning-stage sanity check only.

**Ships in APK (`implementation`):**
- `androidx.core:core-ktx`, `androidx.core:core-splashscreen`
- `androidx.activity:activity-compose`
- `androidx.lifecycle:lifecycle-{runtime-ktx,runtime-compose,viewmodel-compose,viewmodel-navigation3}`
- `androidx.compose.{ui,material3,material:material-icons-extended,ui-tooling-preview}` via the Compose BOM
- `androidx.navigation3:navigation3-{runtime,ui}`
- `androidx.media3:media3-{exoplayer,session,ui}`
- `androidx.room:room-{runtime,ktx}`
- `androidx.datastore:datastore-preferences`
- `androidx.work:work-runtime-ktx`
- `androidx.documentfile:documentfile`
- `androidx.palette:palette-ktx`
- `org.jetbrains.kotlinx:kotlinx-coroutines-guava`
- `org.jetbrains.kotlinx:kotlinx-serialization-json`
- `io.coil-kt.coil3:coil-compose` (transitively pulls okio + okhttp — both Apache 2.0)

**Test-only (`testImplementation` / `androidTestImplementation`, excluded from the APK):**
- `junit:junit:4.13.2` — **EPL-1.0** (Eclipse Public License 1.0). Test-scope only; never shipped. EPL is weak copyleft — it triggers on modifications to JUnit itself, does not infect downstream MIT app code.
- `org.robolectric:robolectric` — MIT
- `androidx.test.*`, `androidx.compose.ui.test.*`, `androidx.arch.core:core-testing`, `androidx.room:room-testing`, `androidx.work:work-testing`, `org.jetbrains.kotlinx:kotlinx-coroutines-test` — Apache 2.0

**Build-only / KSP / Gradle plugins (never shipped):** AGP, KSP, Kotlin Compose / Serialization plugins, Room Gradle plugin, foojay-resolver — Apache 2.0.

Conclusion: **MIT app license is correct. No GPL anywhere. No dep prevents MIT.** This plan ships the NOTICE/attribution surface; the license itself stays MIT.

## Phase A — Licensee plugin + generated inventory

**Why:** every later phase reads the JSON this phase generates.

- [x] **A.1** Added Licensee `1.13.0` to `gradle/libs.versions.toml` (versions + plugins block).
- [x] **A.2** Applied `alias(libs.plugins.licensee)` in `app/build.gradle.kts`.
- [x] **A.3** Configured `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause") }`. EPL-1.0 (junit) is test-scope only and never enters the resolved release classpath Licensee inspects, so no `allowDependency` exemption needed. Reporting-only; `failOnDisallowed` left default.
- [x] **A.4** `androidComponents.onVariants` wires a per-variant `Copy` task (`copyLicensesAssetForDebug` / `copyLicensesAssetForRelease`) that depends on `licenseeAndroid<Variant>` and feeds `merge<Variant>Assets`. Copies `build/reports/licensee/android<Variant>/artifacts.json` into `src/main/assets/licenses/`.
- [x] **A.5** Sourced `Apache-2.0.txt`, `MIT.txt`, `BSD-3-Clause.txt` from `https://spdx.org/licenses/<id>.txt` (canonical SPDX). The plan's hypothesised `EPL-1.0.txt` not needed — confirmed by Licensee that all 213 shipping deps resolve to one of {Apache-2.0, MIT, BSD-3-Clause}.
- [x] **A.6** `:app:assembleDebug` clean. `assets/licenses/artifacts.json` parses (213 entries) with valid SPDX coordinates. Spot-checked: `androidx.media3:media3-exoplayer:1.10.0` → Apache-2.0, `io.coil-kt.coil3:coil-compose:3.1.0` → Apache-2.0, `androidx.room:room-runtime:2.8.4` → Apache-2.0. APK contains all 4 license assets.
- [x] **A.7** Ship + tick.

## Phase B — `LicensesScreen` Compose UI

**Why:** the user-facing surface that fulfils the Apache 2.0 NOTICE requirement.

- [x] **B.1** Added `app/src/main/java/com/eight87/tonearmboy/ui/settings/LicensesScreen.kt`. Reuses `SettingsCard` / `SettingsRow` / `SettingsDimens` chrome.
- [x] **B.2** Read happens directly in `loadLicensesFromAssets(context)` cached via `remember(context)` — single one-shot read. Skipped the dedicated ViewModel since the data is immutable per build; a ViewModel + `StateFlow` would be over-architecture for a static asset read.
- [x] **B.3** `LicenseEntry { groupId, artifactId, version, spdxId, licenseText: String? }` — `licenseText` resolved by `assets/licenses/<spdx>.txt` lookup at construction; unknown SPDX → `licenseText = null` + row renders the `licenses_unknown_spdx` string.
- [x] **B.4** `LazyColumn` of single-row `SettingsCard`s. Card title: `<artifactId> <version>`. Row label: `<groupId>`. Row subtitle: SPDX id. Tap → `AlertDialog` with monospaced license body, scrollable, "Close" button. Verified on AVD.
- [x] **B.5** Navigation route `SettingsLicenses` added to `Destinations.kt`, registered in `TonearmboyApp.kt` + `SettingsRoutes.kt`. AboutScreen gained an `onLicenses` parameter and a new `SettingsRow` "Open-source licenses" between GitHub-repo and License rows.
- [x] **B.6** Strings: `settings_about_licenses_label`, `settings_about_licenses_subtitle`, `licenses_screen_title`, `licenses_row_supporting`, `licenses_unknown_spdx`, `licenses_empty`, `licenses_dialog_close`. All in `values/strings_settings.xml`. German translations need a follow-up sub-agent pass for the new keys.
- [x] **B.7** AVD smoke: Settings root → About → tap "Open-source licenses" → screen lists 213 deps → tap `activity 1.13.0` → dialog shows full Apache-2.0 text in monospaced, scrollable. Confirmed.
- [x] **B.8** Ship + tick.

## Phase C — Tests + audit discipline

**Why:** keep the inventory honest as deps churn.

- [x] **C.1** `LicensesCatalogTest` (Robolectric, JVM-only): parses `assets/licenses/artifacts.json`; asserts non-empty; asserts every entry has a SPDX from the allowlist and a backing license-text asset; asserts the catalog contains the known shipping samples.
- [x] **C.2** `LicensesScreenTest` (Compose UI test under Robolectric): renders the screen, taps the first Apache-2.0 row, asserts the dialog body shows the SPDX text.
- [x] **C.3** AVD smoke: Settings → About → Open-source licenses → list renders → tap row → Apache-2.0 dialog text appears in monospace.
- [x] **C.4** Added an "Open-source licenses" sub-section to `CLAUDE.md` describing the Licensee allowlist, the new-dep workflow (`./gradlew :app:licenseeAndroidDebug`), and the new-SPDX requirement (asset file under `licenses/`).
- [x] **C.5** Ship + tick.

## Out of scope (revisit if pain emerges)

- Failing the build on a disallowed SPDX (`failOnDisallowed = true`) — defer until the v1 inventory has been reviewed once.
- A separate "Acknowledgements" screen for non-binary attributions (icon sets, font samples, sound effects). The current dep tree has none of these; revisit if one lands.
- Multi-locale license text. SPDX bodies are English-only by upstream convention.
- A DEPENDENCIES.md / NOTICE file in repo root duplicating the runtime list. The runtime screen is the canonical surface; a static file would just rot.
