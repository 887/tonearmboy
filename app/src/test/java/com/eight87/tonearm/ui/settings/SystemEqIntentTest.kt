package com.eight87.tonearm.ui.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase H.7.2 — `SystemEqualizer.buildIntent` must carry the three
 * extras the platform requires, plus the canonical action string. The
 * snackbar-fallback path (no resolver) is exercised by checking that
 * `resolves` returns `false` against a context whose package manager
 * has no handler installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemEqIntentTest {

  @Test
  fun intent_carries_action_session_package_and_content_type() {
    val intent = SystemEqualizer.buildIntent(
      audioSessionId = 12345,
      packageName = "com.eight87.tonearm",
    )

    assertEquals(
      AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL,
      intent.action,
    )
    assertEquals(
      12345,
      intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, Int.MIN_VALUE),
    )
    assertEquals(
      "com.eight87.tonearm",
      intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME),
    )
    assertEquals(
      AudioEffect.CONTENT_TYPE_MUSIC,
      intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, Int.MIN_VALUE),
    )
    assertNotNull(intent.action)
  }

  @Test
  fun intent_carries_new_task_flag_for_external_launch() {
    val intent = SystemEqualizer.buildIntent(0, "x")
    assertTrue(
      "FLAG_ACTIVITY_NEW_TASK is required so the system EQ activity " +
        "starts on its own task stack",
      (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
    )
  }

  @Test
  fun resolves_returns_false_when_no_activity_is_registered() {
    // Robolectric's default ShadowPackageManager has no resolver
    // for ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL — that's the
    // documented "stripped-down ROM" code path.
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val intent = SystemEqualizer.buildIntent(
      audioSessionId = 999,
      packageName = context.packageName,
    )
    assertFalse(SystemEqualizer.resolves(context, intent))
  }

  @Test
  fun resolves_returns_true_when_activity_is_registered() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val intent = SystemEqualizer.buildIntent(
      audioSessionId = 1000,
      packageName = context.packageName,
    )

    val resolveInfo = android.content.pm.ResolveInfo().apply {
      activityInfo = android.content.pm.ActivityInfo().apply {
        packageName = "com.fake.eqapp"
        name = "com.fake.eqapp.EqActivity"
      }
    }
    shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)

    assertTrue(SystemEqualizer.resolves(context, intent))
  }
}
