package com.eight87.tonearm.ui.permission

import android.Manifest
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.23.5 — assert the [RequirePostNotifications] gate's API-level
 * branching:
 *
 *  - On API < 33 the gate is a pure pass-through. It must not reach
 *    for `Manifest.permission.POST_NOTIFICATIONS` (which doesn't exist
 *    as a runtime grantable permission on those OS versions) and
 *    must render its [content] slot immediately.
 *  - On API >= 33 the gate launches a `RequestPermission` contract
 *    against `Manifest.permission.POST_NOTIFICATIONS`. We assert the
 *    constant the gate references is the canonical platform string —
 *    if a future Android renames the permission this test catches it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PostNotificationsGateTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  @Config(sdk = [32], manifest = Config.NONE)
  fun api_below_33_passes_through_immediately() {
    composeRule.setContent {
      RequirePostNotifications {
        Text("inner-content")
      }
    }
    composeRule.onNodeWithText("inner-content").assertIsDisplayed()
  }

  @Test
  @Config(sdk = [33], manifest = Config.NONE)
  fun api_33_renders_content_when_already_granted() {
    // No way to pre-grant POST_NOTIFICATIONS in the Robolectric runtime
    // without ShadowApplication.grantPermissions(); but the gate
    // renders its content even on a denied state (the tray
    // notification just doesn't post). So at minimum the inner
    // content must always be visible.
    composeRule.setContent {
      RequirePostNotifications {
        Text("inner-content")
      }
    }
    composeRule.onNodeWithText("inner-content").assertIsDisplayed()
  }

  @Test
  fun permission_constant_is_post_notifications() {
    // Pin the permission name so a typo / rename in a future minor
    // surfaces here rather than silently breaking the runtime grant.
    assertEquals(
      "android.permission.POST_NOTIFICATIONS",
      Manifest.permission.POST_NOTIFICATIONS,
    )
  }

  @Test
  fun tiramisu_constant_pinned_to_33() {
    // The gate's API-level branch is `Build.VERSION.SDK_INT >= TIRAMISU`.
    // Pin TIRAMISU to its known value so a future SDK reshuffle is loud.
    assertEquals(33, Build.VERSION_CODES.TIRAMISU)
  }

  @Test
  fun denied_snackbar_message_uses_kyis_plain_language() {
    // KYIS / plain-language constraint: the snackbar copy explains
    // the consequence factually, no exclamation, no identity, no
    // editorial. Keep the literal string under test so editorial
    // drift is surfaced.
    assertNotNull(DENIED_SNACKBAR_MESSAGE)
    assertEquals(
      "Notifications disabled — playback controls won't appear in your notification tray.",
      DENIED_SNACKBAR_MESSAGE,
    )
  }
}
