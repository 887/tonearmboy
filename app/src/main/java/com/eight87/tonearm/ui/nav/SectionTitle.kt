package com.eight87.tonearm.ui.nav

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Per-section title shown in the root [androidx.compose.material3.TopAppBar].
 *
 * Each destination sets its own title in a `LaunchedEffect(Unit)` on
 * entry, e.g. `LocalSectionTitle.current.value = "Songs"`. The
 * root scaffold reads this value and re-renders. No cleanup is required
 * because the next destination's effect overwrites the previous value.
 *
 * The default value is the app name; that gets overwritten before the
 * user sees anything because [TonearmApp]'s root entry installs the
 * library-tab title immediately.
 */
val LocalSectionTitle = compositionLocalOf<MutableState<String>> {
  // A non-error default so previews and tests don't need to provide it.
  mutableStateOf("tonearm")
}
