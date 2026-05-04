package com.eight87.tonearmboy.playback

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.playback.replaygain.computeGain
import com.eight87.tonearmboy.playback.replaygain.linearGainFromDb
import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * R.C.4 — owns ReplayGain settings + the MediaController.volume
 * application logic. Removes the LibraryRepository / TrackSource
 * handle from `PlaybackUiController` entirely; the controller now
 * just forwards [ReplayGainCommands.setReplayGain] here and asks
 * for an immediate apply on track transitions.
 *
 * Setup pattern from `AppGraph`:
 *   ```
 *   val replayGain = ReplayGainController(scope = playbackScope, controllerProvider = { … })
 *   replayGain.setLibrary(graph.tracks)
 *   ```
 *
 * The library handle is set once and re-read every apply, so a late
 * library binding (e.g. cold start where AppGraph sets it after the
 * controller has already begun connecting) doesn't need to refire
 * the apply.
 */
@UnstableApi
internal class ReplayGainController(
  private val scope: CoroutineScope,
  private val controllerProvider: () -> MediaController?,
) : ReplayGainCommands {

  /**
   * D.9b.1 / D.9b.2 — current ReplayGain strategy + pre-amp dB,
   * pushed in by the activity's settings observer. Volatile so the
   * Player.Listener (in `PlaybackUiController`) can call
   * [applyReplayGainNow] from any thread.
   */
  @Volatile
  private var strategy: ReplayGainStrategy = ReplayGainStrategy.Off

  @Volatile
  private var preampDb: Float = 0f

  /**
   * Library handle used to look up album-level gain + album track
   * count when recomputing the gain on a track transition. When
   * null, falls back to track gain from the [MediaItem] metadata.
   */
  @Volatile
  private var library: TrackSource? = null

  fun setLibrary(repo: TrackSource) {
    library = repo
  }

  /**
   * Update the current ReplayGain settings and re-apply the
   * resulting volume to the active player.
   */
  override fun setReplayGain(strategy: ReplayGainStrategy, preampDb: Float) {
    this.strategy = strategy
    this.preampDb = preampDb
    scope.launch { applyReplayGainNow() }
  }

  /**
   * Recompute the current track's gain and write it to
   * [MediaController.setVolume]. Looks up the active track + album
   * from the library cache; falls back gracefully when the cache
   * is empty (e.g. tests).
   */
  suspend fun applyReplayGainNow() {
    val ctl = controllerProvider() ?: return
    val item = ctl.currentMediaItem ?: run {
      ctl.volume = 1f
      return
    }
    val trackId = item.mediaId.toLongOrNull()
    val track = trackId?.let { library?.trackById(it) }
    val trackGainDb = track?.replayGainTrackDb
    val (albumGainDb, _) = if (track != null) {
      library?.albumReplayGain(track.album, track.albumArtist ?: track.artist)
        ?: (null to null)
    } else {
      null to null
    }
    val coverage = if (track == null) 0f else computeQueueAlbumCoverage(ctl, track)
    val gainDb = computeGain(strategy, trackGainDb, albumGainDb, coverage)
    val total = gainDb + preampDb
    val volume = linearGainFromDb(total)
    Log.i(
      TAG,
      "applyReplayGain strategy=$strategy preamp=$preampDb " +
        "trackDb=$trackGainDb albumDb=$albumGainDb coverage=$coverage " +
        "totalDb=$total volume=$volume",
    )
    // Player.setVolume runs on the Main looper; we're already there
    // when invoked from the listener.
    ctl.volume = volume
  }

  /**
   * Estimate how much of the playing track's album is queued.
   * Smart-mode trips into album behaviour at >= 75% (see
   * `SMART_THRESHOLD`). We compare the count of queued items that
   * share the playing track's `(album, albumArtist|artist)` key
   * against the library's full count of tracks for that album.
   */
  private suspend fun computeQueueAlbumCoverage(
    ctl: MediaController,
    playing: Track,
  ): Float {
    val albumName = playing.album ?: return 0f
    val albumArtistKey = playing.albumArtist ?: playing.artist
    var queuedFromAlbum = 0
    val lib = library
    val n = ctl.mediaItemCount
    for (i in 0 until n) {
      val mi = ctl.getMediaItemAt(i)
      val mid = mi.mediaId.toLongOrNull() ?: continue
      // Cheap path: the MediaMetadata carries albumTitle / albumArtist
      // for items already in the queue, so we don't have to round-trip
      // every queue item through the DB.
      val md = mi.mediaMetadata
      val miAlbum = md.albumTitle?.toString()
      val miAlbumArtist = md.albumArtist?.toString() ?: md.artist?.toString()
      if (miAlbum == albumName && miAlbumArtist == albumArtistKey) queuedFromAlbum++
      else if (miAlbum == null && lib != null) {
        // Fall back to a DB lookup when the queue item came from a
        // surface that didn't tag the metadata.
        val t = lib.trackById(mid) ?: continue
        if (t.album == albumName && (t.albumArtist ?: t.artist) == albumArtistKey) queuedFromAlbum++
      }
    }
    val totalForAlbum = lib?.trackCountForAlbum(albumName, albumArtistKey) ?: queuedFromAlbum
    return if (totalForAlbum <= 0) 0f else queuedFromAlbum.toFloat() / totalForAlbum.toFloat()
  }

  companion object {
    private const val TAG = "tonearmboy-rg"
  }
}
