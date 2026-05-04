package com.eight87.tonearmboy.playback

import android.app.PendingIntent
import android.content.Context

/**
 * R.E.8 — boundary that lets [PlaybackService] hand the system a
 * `PendingIntent` for the now-playing screen without importing
 * `MainActivity` (or the entire UI module).
 *
 * Production binding: `MainActivitySessionIntentFactory` in
 * `ui/nav/`, which is the only place that knows the activity class.
 * Tests can stub this to assert the service's notification wiring
 * without spinning a UI process.
 */
fun interface SessionActivityIntentFactory {
  /**
   * Build the `PendingIntent` that the user lands on when tapping the
   * MediaStyle notification. Convention: route to the now-playing
   * surface, dispatching through `onNewIntent` (FLAG_ACTIVITY_SINGLE_TOP
   * + FLAG_ACTIVITY_CLEAR_TOP) so a tap on an already-running activity
   * reuses it instead of spawning a duplicate.
   */
  fun nowPlayingPendingIntent(context: Context): PendingIntent
}
