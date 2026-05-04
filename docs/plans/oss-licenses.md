# tonearmboy — open-source licenses plan

## Status: 🟡 PLANNED

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

- [ ] **A.1** Add Licensee version to `gradle/libs.versions.toml` and a `[plugins]` entry: `licensee = { id = "app.cash.licensee", version.ref = "licensee" }`. Pin to the latest stable.
- [ ] **A.2** Apply `alias(libs.plugins.licensee)` in `app/build.gradle.kts`.
- [ ] **A.3** Configure the plugin block: `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause"); allowDependency("junit", "junit", "4.13.2") { because("EPL-1.0; test-scope only, not shipped") } }`. Reporting only in v1; do not set `failOnDisallowed`.
- [ ] **A.4** Wire a Gradle task to copy `app/build/reports/licensee/release/artifacts.json` to `app/src/main/assets/licenses/artifacts.json` so the runtime can read it via `AssetManager`. Wire as a dependency of `mergeReleaseAssets` (and `mergeDebugAssets`) so a fresh build is always self-consistent.
- [ ] **A.5** Add `app/src/main/assets/licenses/Apache-2.0.txt`, `MIT.txt`, `EPL-1.0.txt`. Source from SPDX official text. Top-of-file comment notes the SPDX id and the source URL.
- [ ] **A.6** Run `:app:assembleDebug` once. Verify `app/src/main/assets/licenses/artifacts.json` exists, parses, and contains a non-empty array. Spot-check three entries against the inventory snapshot.
- [ ] **A.7** Ship + tick.

## Phase B — `LicensesScreen` Compose UI

**Why:** the user-facing surface that fulfils the Apache 2.0 NOTICE requirement.

- [ ] **B.1** Add `app/src/main/java/com/eight87/tonearmboy/ui/settings/LicensesScreen.kt`. Reuse the M3-Expressive grouped-card chrome (`SettingsCard` / `SettingsRow` / `SettingsRowDivider` / `SettingsDimens`) for visual consistency with `AboutScreen`.
- [ ] **B.2** Add `LicensesViewModel`. Reads `assets/licenses/artifacts.json` once at init via `AssetManager.open(...)` + `kotlinx.serialization.json`. Exposes `StateFlow<List<LicenseEntry>>` (entries pre-sorted by `groupId:artifactId`). One-shot read; not a `Flow`.
- [ ] **B.3** Define `LicenseEntry { groupId, artifactId, version, spdxId, licenseText: String? }` — `licenseText` resolved at construction by reading `assets/licenses/<spdx>.txt`. Unknown SPDX → `licenseText = null` and the row renders an "Unknown SPDX" warning (catches missing-text-asset cases at runtime in addition to the test).
- [ ] **B.4** UI: `LazyColumn` of `SettingsRow`s. Row title: `<artifactId> <version>`. Row supporting text: `<groupId> • <spdxId>`. Tap → expand inline (or open a Material3 `Dialog` showing the license body in monospaced text, scrollable). Match whichever pattern `AboutScreen`'s "view license" interaction already uses; fall back to dialog if there is none.
- [ ] **B.5** Add the navigation route. Wire from the existing `AboutScreen` "Source" card — a new `SettingsRow` "Open-source licenses" with an `Icons.Outlined.Article` icon, between the GitHub repo row and the MIT license row.
- [ ] **B.6** Strings: every label resource-backed in `values/strings.xml` per the i18n discipline locked in `docs/plans/translations.md` Phase T.A. Keys: `licenses_screen_title`, `licenses_row_label`, `licenses_row_supporting`, `cd_licenses_back`, `licenses_unknown_spdx`.
- [ ] **B.7** Verify on AVD: tap About → tap "Open-source licenses" → list renders → tap a row → license body appears.
- [ ] **B.8** Ship + tick.

## Phase C — Tests + audit discipline

**Why:** keep the inventory honest as deps churn.

- [ ] **C.1** `LicensesCatalogTest` (Robolectric, JVM-only): parses `assets/licenses/artifacts.json`; asserts non-empty; asserts every entry has a recognized SPDX from the allowlist and a backing license-text asset; asserts the catalog contains a known shipping sample (`androidx.media3:media3-exoplayer`, `io.coil-kt.coil3:coil-compose`, `androidx.room:room-runtime`).
- [ ] **C.2** `LicensesScreenTest` (Compose UI test under Robolectric, `ui-test-junit4`): renders, scrolls, expanding a row reveals license text.
- [ ] **C.3** AVD smoke (per CLAUDE.md UI-verification rule).
- [ ] **C.4** Add a one-paragraph "Licenses" subhead to `CLAUDE.md`: when adding a new `implementation` dep, run `:app:licenseeReport` and confirm the SPDX is in the allowlist; if not, either add it to `licensee.allow(...)` (preferred) or document the exemption with a `because("...")`. Include a pointer to this plan.
- [ ] **C.5** Ship + tick.

## Out of scope (revisit if pain emerges)

- Failing the build on a disallowed SPDX (`failOnDisallowed = true`) — defer until the v1 inventory has been reviewed once.
- A separate "Acknowledgements" screen for non-binary attributions (icon sets, font samples, sound effects). The current dep tree has none of these; revisit if one lands.
- Multi-locale license text. SPDX bodies are English-only by upstream convention.
- A DEPENDENCIES.md / NOTICE file in repo root duplicating the runtime list. The runtime screen is the canonical surface; a static file would just rot.
