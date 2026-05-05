# tonearmboy — Auxio-style swipe gestures (mini-player ↔ NowPlaying)

## Status: ✅ DONE — Auxio-style swipe gestures shipped.

## Why

Auxio's mini-player is a **draggable surface**. Pull up on it → the full
NowPlaying screen rises into view. Pull down on it → playback dismisses
(equivalent to tapping the close X). NowPlaying itself accepts a
swipe-down-from-top to go back. That's the gesture vocabulary the user
expects to find here too.

Today (pre-G):

- mini-player → NowPlaying is **tap-only** on the info row.
- NowPlaying → mini-player (back) is **back-arrow only**.
- The close X dismisses playback; nothing else does.

After G:

- mini-player **swipe up** → push NowPlaying (same effect as the existing tap).
- mini-player **swipe down** → close playback (same effect as the existing X).
- mini-player **tap** → push NowPlaying (unchanged — the existing affordance for tap-from-recents and accessibility).
- mini-player **close X** → close playback (unchanged — the explicit fallback).
- NowPlaying **swipe down from the top** → pop back to the previous destination (back-arrow still works).
- NowPlaying **swipe down mid-queue** → keeps scrolling the queue, nothing else.
- **Notification deeplink unchanged** — `MainActivity.handleIntent` still routes to NowPlaying via the deeplink reactor in `TonearmboyApp`.

## Locked decisions

- **No animated "rising sheet" transition.** This is gesture handling on top of the existing nav-3 push, not a custom modal sheet. Visual polish (the actual rise-from-bottom feel) is a follow-up; this phase wires the gesture, the navigation effect is the same `backStack.push(NowPlaying)` / `backStack.pop()`.
- **64 dp threshold** for both up-swipe and down-swipe on the mini-player. Below threshold = no-op (rebound to rest). Tunable later.
- **NestedScrollConnection at the NowPlaying scaffold root** is the right abstraction for swipe-down-from-top — it composes with the queue's `LazyColumn` automatically so mid-list drags scroll the list, only over-scroll-down-at-top accumulates toward dismissal.
- **Click + drag coexist** on the mini-player info row. `combinedClickable` already plays nice with `pointerInput { detectVerticalDragGestures }` since the gesture-detector recognises a tap (no movement) as not-a-drag.

## Phase G.1 — mini-player swipe up / down

- [x] **G.1.1** Add `Modifier.pointerInput { detectVerticalDragGestures }` to the MiniPlayer info row. Track accumulated vertical delta in a `mutableStateOf<Float>(0f)`. On `onDragEnd`, threshold-check:
  - `delta < -threshold` (swiped up) → call `onExpand`
  - `delta > threshold` (swiped down) → call `onClose`
  - else → no-op (rebound)
  - reset delta to 0 in all cases.
- [x] **G.1.2** Threshold = 64 dp converted via `with(LocalDensity.current) { 64.dp.toPx() }`. Captured into a local val so the gesture handler can compare without recomputing per-event.
- [x] **G.1.3** Verify the existing `clickable(onClick = onExpand)` on the info row still fires for taps — `detectVerticalDragGestures` only consumes events when an actual drag is in progress.
- [x] **G.1.4** Robolectric or AVD smoke: swipe up opens NowPlaying; swipe down closes; tap still opens NowPlaying.

## Phase G.2 — NowPlaying swipe-down-from-top to pop

- [x] **G.2.1** Add a `NestedScrollConnection` at the NowPlaying scaffold's body modifier.
  - `onPostScroll`: when the inner `LazyColumn` couldn't consume a downward drag (i.e. it's at the top of its scroll), accumulate the leftover positive `available.y` into a state variable. Consume that delta so it doesn't bubble further.
  - `onPreScroll`: when the user starts dragging back up while we have accumulated over-scroll, drain it first (matching Material's pull-to-refresh shape).
  - `onPreFling`: if accumulated over-scroll exceeds 64 dp at fling time, call `onBack`. Reset accumulator to 0.
- [x] **G.2.2** Threshold = 64 dp, same as the mini-player. Same call-site density-px conversion.
- [x] **G.2.3** Verify mid-queue scrolling is untouched — when the LazyColumn is mid-scroll, `available.y` from `onPostScroll` is 0 (consumed) so the accumulator doesn't grow.
- [x] **G.2.4** AVD smoke: swipe down from top of queue pops back to library; swipe down mid-queue scrolls the queue normally.

## Phase G.3 — verify notification deeplink + back-arrow paths still work

- [x] **G.3.1** AVD smoke: tap notification while in any other route → deeplink reactor still pushes NowPlaying (gesture changes don't touch `TonearmboyApp.deeplink` LaunchedEffect).
- [x] **G.3.2** AVD smoke: NowPlaying back-arrow still works (the back arrow is a separate `IconButton`, not affected by the gesture).
- [x] **G.3.3** AVD smoke: explicit close X on the mini-player still dismisses playback.

## Phase G.4 — ship + tick

- [x] **G.4.1** Tick every G.1 / G.2 / G.3 sub-step.
- [x] **G.4.2** Mark the phase header with the commit range.
- [x] **G.4.3** Merge to main, push, ship release.

**Effort:** S (1–2 hours). **Risk:** low — gesture additions, no behaviour replacement; existing tap + back-arrow + close-X paths stay intact.
