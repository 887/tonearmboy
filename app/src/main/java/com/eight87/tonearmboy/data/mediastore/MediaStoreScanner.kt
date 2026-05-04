package com.eight87.tonearmboy.data.mediastore

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.data.model.ScannedTrack
import com.eight87.tonearmboy.playback.replaygain.ReplayGainTagReader

/**
 * Pulls audio metadata out of MediaStore. Returns [ScannedTrack]
 * instances; callers never see a raw `Cursor`.
 *
 * R.F.10 — composition only. Pass-1 cursor walk lives in
 * [MediaStoreCursorReader], pass-2 ReplayGain reads in
 * [ReplayGainEnricher]. (Data-F5.)
 *
 * Genre is fetched via `MediaStore.Audio.Genres.Members` rather than
 * the deprecated `MediaStore.Audio.Media.GENRE` column.
 *
 * The scanner does no caching of its own — it always returns the
 * authoritative MediaStore snapshot. Caching is the repository's job.
 */
@UnstableApi
class MediaStoreScanner(
  context: Context,
  replayGainReader: ReplayGainTagReader = ReplayGainTagReader(context),
) {
  private val reader = MediaStoreCursorReader(context)
  private val enricher = ReplayGainEnricher(replayGainReader)

  /**
   * Snapshot every audio file MediaStore knows about. Returns an empty
   * list if the audio permission is missing or MediaStore returns no
   * cursor.
   *
   * D.9b.1: every track is also probed for `REPLAYGAIN_TRACK_*` and
   * `REPLAYGAIN_ALBUM_*` tags.
   *
   * D.9d.1: when [scopePathPrefixes] is non-empty, the scan is
   * restricted to MediaStore rows whose `DATA` column begins with one
   * of the supplied filesystem prefixes. Empty = scan everything.
   */
  suspend fun scanTracks(
    separators: Set<String> = emptySet(),
    scopePathPrefixes: Set<String> = emptySet(),
    onProgress: ((scanned: Int, total: Int, currentTitle: String?) -> Unit)? = null,
  ): List<ScannedTrack> {
    val stubs = reader.read(separators, scopePathPrefixes)
    if (stubs.isEmpty()) return emptyList()
    return enricher.enrich(stubs, onProgress)
  }

  /** The content URI for an individual audio item, useful for callers. */
  fun uriFor(trackId: Long): Uri = reader.uriFor(trackId)
}
