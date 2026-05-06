# cold-start-perf — make tonearmboy launch faster

> Reference: [Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)

The user's complaint: cold start takes ~1.8s on the AVD vs Auxio's
sub-100ms. The structural gap is real — Auxio uses Android Views,
tonearmboy uses Jetpack Compose, and Compose pays a ~150–300ms
runtime warm-up cost on every cold launch that Views simply does not.
**Sub-100ms is not reachable in Compose.** The realistic floor for a
non-trivial Compose app on mid-range hardware with all the right
boxes ticked is **~300–400ms**. Every phase below is about closing
the gap toward that floor.

Cold start budget on the AVD (run #1) before this plan:
- 530 ms process boot — Android-fixed, can't change
- ~200 ms Compose runtime warm-up — Compose-fixed
- ~400 ms first composition — phases A–E target this
- ~600 ms layout + draw + post-composition recompositions — phases A–E + F target this

## Phase A — defer the heavy NowPlaying surface — shipped in commit `3436ae2`

- [x] **A.1** Profile MainActivity onCreate with `SystemClock.elapsedRealtime` markers around `super.onCreate`, `AppGraph.get`, `setContent`, the Theme entry, the perm gate exit, the first LaunchedEffect-after-composition. Confirmed first composition done at 586 ms after `setContent` call.
- [x] **A.2** Identify the NowPlayingScreen unconditional mount inside the sheet stack. The original "BOTH children always composed" comment justified keeping the MiniPlayer composition alive across drags — but it doesn't apply to NowPlaying, which is NOT the gesture owner.
- [x] **A.3** Gate `NowPlayingScreen { ... }` on `progress > 0.45f` (where the staggered crossfade lifts `nowPlayingAlpha` off zero). MiniPlayer stays always-mounted; the heavy queue surface composes only when the sheet is rising.
- [x] **A.4** Re-measure: first composition done dropped from 586 ms → 409 ms (~177 ms saved, 30%). AVD-verified.

## Phase B — fix the task-switch resurrection bug — shipped in commit `3436ae2`

- [x] **B.1** Reproduce: app launched once via notification → `EXTRA_DEEPLINK` baked into the launch intent → every later activity recreation (memory pressure, config change, task-switcher resurrection) re-runs `handleIntent` against the saved intent and re-fires the deeplink, animating the NowPlaying sheet open even when the user had collapsed it.
- [x] **B.2** Strip `EXTRA_DEEPLINK` from the intent immediately after consumption: `intent.removeExtra(...)` + `setIntent(intent)` so the activity's stored intent is idempotent for re-reads.

## Phase C — drop animateColorAsState in the theme — shipped in commit `865d942`

- [x] **C.1** `TonearmboyTheme` allocated 9 `animateColorAsState(tween(300))` calls — surface, surfaceVariant, background, the 5-tier surfaceContainer ladder, secondaryContainer. Each allocates a `State<Color>` + a `LaunchedEffect` to track the animation, even when no album is playing (tint = null) so the animation target equals the base value.
- [x] **C.2** Replace with a guarded `if (tint == null) baseScheme else baseScheme.copy(...)` using direct `blendSurface()` calls. Pure function, no per-color State, no LaunchedEffects on cold start.
- [x] **C.3** Trade-off documented: album→album crossfade is now an instant snap rather than a 300 ms tween. Acceptable for the startup-cost saving — colours still update correctly, just not animated.

## Phase D — switch root-level collects to lifecycle-aware — shipped in commit `865d942`

- [x] **D.1** Audit MainActivity for `collectAsState` (vs `collectAsStateWithLifecycle`). Found 6: theme, baseTheme, albumArtTintEnabled, customChromeTint, playbackState, albumPalette.
- [x] **D.2** Each subscribes to its Flow during composition regardless of lifecycle — so DataStore reads kick off immediately, and their first emissions trigger root recomposition cascades on the critical first-frame path.
- [x] **D.3** Switch all 6 to `collectAsStateWithLifecycle`. Collection now starts at `Lifecycle.STARTED`, deferring DataStore reads (and the recompositions their first emissions trigger) until after the first frame ships.

## Phase E — replace BoxWithConstraints in TonearmboyApp — shipped in commit `865d942`

- [x] **E.1** `TonearmboyApp` opened with `BoxWithConstraints { val screenHeightDp = maxHeight; ... }` purely to read screen height for sheet drag math + the inner-stack height. `BoxWithConstraints` is a `SubcomposeLayout` — it forces a full extra composition pass for the children after parent measure resolves.
- [x] **E.2** Replace with a plain `Box(modifier = Modifier.fillMaxSize())` reading `LocalConfiguration.current.screenHeightDp.dp` ahead of measurement. No subcompose, no extra composition pass. The `fillMaxSize()` parent makes the configuration height equal to what `maxHeight` returned, so sheet drag math is unchanged.

## Phase J — strip MainActivity to the shutterboy shape — shipped in commit `<pending>`

This was the biggest single win. Direct comparison: shutterboy
MainActivity is 25 lines and cold-starts ~1100 ms; pre-J tonearmboy
MainActivity was 220 lines and cold-started ~1500 ms. Same Compose
runtime, same M3E theme — the gap was entirely the work tonearmboy
did synchronously in onCreate.

- [x] **J.1** Direct A/B against shutterboy on the same AVD. Pre-fix tonearmboy ~1500 ms median, shutterboy ~1100 ms median. Confirmed it's not Compose-the-runtime; it's *what tonearmboy specifically does* on cold start.
- [x] **J.2** Drop the splash-hold mechanism (`splash.setKeepOnScreenCondition { splashHold.get() }` + the 150-ms `withTimeoutOrNull` waiting on `playbackUiController.awaitConnected()`). The original D.22.2 fix was for a "blank black activity frame race" when the activity was killed but the foreground service was still alive — but with phase A's lazy NowPlaying mount and the existing `ConnectionPhase.Connecting` sub-state, that race no longer surfaces. shutterboy ships nothing of the sort.
- [x] **J.3** Defer the three `applicationScope.launch { ... }` chains that ran in onCreate (watcher re-arm, `firstLaunchInitialise`, the splash-hold connect kick) into `LaunchedEffect(Unit) { }` blocks inside `setContent`. They run after the first composition completes, off the critical first-frame path. Idempotent against the controller / service's own startup logic so nothing breaks.
- [x] **J.4** Re-measure on the same AVD: tonearmboy median 1195 ms, shutterboy median 1177 ms. **<20 ms gap, down from a ~400 ms gap.**

## Phase F — Baseline Profile — pending

The biggest remaining lift. Per the Android best-practices page, a
Baseline Profile typically shaves **25–35% off cold start** on its
own — worth more than every phase above combined.

- [ ] **F.1** Add the `androidx.baselineprofile` Gradle plugin + a sibling `baselineprofile` benchmark module with a `MacrobenchmarkRule` that records the cold-boot path (launch → library tab visible).
- [ ] **F.2** Run the recording task on a connected device / AVD, generate `app/src/main/baseline-prof.txt`.
- [ ] **F.3** Add `androidx.profileinstaller:profileinstaller` to the app module so the profile actually installs on cold boot.
- [ ] **F.4** Re-measure cold start with `am start -W` cold-cold-cold over 5 runs, before vs after.
- [ ] **F.5** Wire Baseline Profile generation into the release script (`scripts/build-release-apk.sh`) so every shipped APK carries an up-to-date profile.

Separate plan-of-work because it adds a build module and a new
gradle plugin — too invasive for the same session as A–E.

## Phase G — phase-aware deferral — pending

From the [best practices page](https://developer.android.com/develop/ui/compose/performance/bestpractices):
*"By switching to the lambda version of the modifier, you can make
sure the function reads the scroll state in the layout phase. As a
result, when the scroll state changes, Compose can skip the
composition phase entirely."*

- [ ] **G.1** Audit `Modifier.offset`, `Modifier.alpha`, `Modifier.padding`, `Modifier.graphicsLayer` callsites for the value-form-vs-lambda-form split. Lambda-form modifiers (`offset { }`, `alpha { }`, `graphicsLayer { }`) defer the State read to the layout / draw phase — composition can skip when the read state changes.
- [ ] **G.2** Sweep TonearmboyApp's sheet — `Modifier.alpha(miniAlpha)` and `Modifier.alpha(nowPlayingAlpha)` are value-form reads of `sheetProgress`. Convert to `Modifier.graphicsLayer { alpha = ... }` so a sheet-drag tick re-layouts without re-composing.
- [ ] **G.3** Audit FastScrollbar's thumb position math (`offset(y = thumbTopDp)`) — same fix.

## Phase H — minimise composable bodies — pending

*"Composable functions can run very frequently... you should do as
little calculation in the body of your composable as you can. ... If
possible, it's best to move calculations outside of the composable
altogether."*

- [ ] **H.1** Sweep TonearmboyApp's body for derived values that should be `remember(keys) { ... }` — `letterFor()`, `formatArtistSubtitle()`, sort comparators, etc. Some are already cached; verify all.
- [ ] **H.2** Hoist any pure derivations that don't depend on Compose state out of composables entirely.

## Phase I — avoid backwards writes — audit pending

*"Compose has a core assumption that you will never write to state
that has already been read in the same composition... it can cause
recomposition to occur on every frame, endlessly."*

- [ ] **I.1** Grep for `var x by remember { mutableStateOf(...) }` followed by a write inside the same composable function body. The user has not reported any infinite-recompose loops, but this is the kind of latent bug that explains a "feels slow" startup.

## Status

Phases A–E + J shipped (commits `3436ae2`, `865d942`, plus the J commit landed alongside this plan revision). **Tonearmboy now matches shutterboy** to within 20 ms on the same AVD. Phases F–I pending — F (Baseline Profile) is the next biggest win on real-device hardware; G–I are tail polish.
