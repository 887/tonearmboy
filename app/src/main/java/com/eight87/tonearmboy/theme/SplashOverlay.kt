package com.eight87.tonearmboy.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import kotlinx.coroutines.delay

/**
 * Two-stage splash, frame two.
 *
 * The Android 12+ SplashScreen API hard-clips its icon to a circle —
 * no theme attribute disables that. Frame one of our splash points
 * `windowSplashScreenAnimatedIcon` at an empty AnimatedVectorDrawable
 * so the system circle area paints nothing and the cold-start frame
 * is just the solid `launcher_background` colour. This composable is
 * frame two: it overlays the activity content with the FULL SQUARE
 * launcher artwork, fades out after [holdMs], and lets the real UI
 * take over.
 *
 * Net result on stock Pixel: cold start → flat launcher_background
 * (no circle visible) → full square fox + vinyl player → home screen.
 *
 * Caveat: some OEM skins (Xiaomi notably) draw a black circle mask
 * under the empty icon during frame one. Best-effort.
 */
@Composable
fun SplashOverlay(
  holdMs: Long = 700L,
  fadeMs: Int = 220,
) {
  // `visible` flips false after `holdMs`; AnimatedVisibility fades the
  // overlay out over `fadeMs` so the real UI doesn't pop in.
  var visible by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    delay(holdMs)
    visible = false
  }
  AnimatedVisibility(
    visible = visible,
    enter = androidx.compose.animation.EnterTransition.None,
    exit = fadeOut(animationSpec = tween(durationMillis = fadeMs)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(colorResource(R.color.launcher_background)),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(220.dp),
      )
    }
  }
}
