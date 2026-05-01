package com.eight87.tonearm.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Compose-friendly helpers for the audio library permission flow.
 *
 * On Android 13+ (API 33) the audio library is gated by
 * [Manifest.permission.READ_MEDIA_AUDIO]. On API 32 and below the legacy
 * [Manifest.permission.READ_EXTERNAL_STORAGE] applies. The UI layer in
 * Phase D is expected to call [requiredAudioPermission] and pass the
 * result to `rememberLauncherForActivityResult` /
 * `ActivityResultContracts.RequestPermission`.
 *
 * This module never asks for the permission directly — the UI owns the
 * launcher lifecycle. We only expose the static names + a check helper
 * so non-UI callers (the smoke-test entry point, the repository scan)
 * can fail fast when permission is missing.
 */
object MediaStorePermissions {

  /** The single permission needed to read audio metadata on the current OS. */
  fun requiredAudioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_AUDIO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }

  /** True when the app currently holds the audio permission. */
  fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredAudioPermission()) ==
      PackageManager.PERMISSION_GRANTED
}
