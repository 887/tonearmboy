package com.eight87.tonearm.playback.replaygain

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import java.util.concurrent.TimeUnit

/**
 * Pulls ReplayGain tag values out of a media file using Media3's
 * [MetadataRetriever]. ID3v2 surfaces them as `TXXX` frames where
 * `description` is e.g. `REPLAYGAIN_TRACK_GAIN` and the first
 * `values` entry is `"-6.34 dB"`. Vorbis comments (FLAC, OGG, Opus)
 * surface them as [VorbisComment] entries with the same key+value pair.
 *
 * The retriever is run synchronously per track on the scanner's IO
 * thread (the scan already runs on `Dispatchers.IO`). Each call is
 * bounded by [RETRIEVE_TIMEOUT_MS] so a malformed file can't stall
 * the whole rescan.
 */
@UnstableApi
class ReplayGainTagReader(private val context: Context) {

  /**
   * Read ReplayGain values for [uri]. Returns a [ReplayGainTags]
   * snapshot — any individual field may be null when the file lacks
   * the corresponding tag.
   *
   * Errors are swallowed (logged at warn) and surface as an empty
   * [ReplayGainTags] so a single malformed file doesn't break the
   * scan.
   */
  fun read(uri: Uri): ReplayGainTags {
    return try {
      val item = MediaItem.fromUri(uri)
      val future = MetadataRetriever.retrieveMetadata(context.applicationContext, item)
      val groups = future.get(RETRIEVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

      var trackGainDb: Float? = null
      var trackPeak: Float? = null
      var albumGainDb: Float? = null
      var albumPeak: Float? = null

      for (g in 0 until groups.length) {
        val group = groups.get(g)
        for (f in 0 until group.length) {
          val format = group.getFormat(f)
          val metadata: Metadata = format.metadata ?: continue
          for (i in 0 until metadata.length()) {
            val pair = extractKeyValue(metadata.get(i)) ?: continue
            val (key, value) = pair
            when (key) {
              "REPLAYGAIN_TRACK_GAIN" -> trackGainDb = trackGainDb ?: ReplayGainParser.parseGainDb(value)
              "REPLAYGAIN_TRACK_PEAK" -> trackPeak = trackPeak ?: ReplayGainParser.parsePeak(value)
              "REPLAYGAIN_ALBUM_GAIN" -> albumGainDb = albumGainDb ?: ReplayGainParser.parseGainDb(value)
              "REPLAYGAIN_ALBUM_PEAK" -> albumPeak = albumPeak ?: ReplayGainParser.parsePeak(value)
            }
          }
        }
      }
      ReplayGainTags(trackGainDb, trackPeak, albumGainDb, albumPeak)
    } catch (t: Throwable) {
      Log.w(TAG, "ReplayGain extraction failed for $uri: ${t.message}")
      ReplayGainTags.Empty
    }
  }

  /**
   * Map a Media3 [Metadata.Entry] to a (KEY, value) pair when the
   * entry is one of the formats we know carry ReplayGain.
   *
   * - [TextInformationFrame] / `TXXX` — id3v2; the key is in
   *   `description`, the value is the first `values` entry.
   * - [VorbisComment] — flac/ogg/opus; `key` and `value` are the
   *   comment's key+value directly.
   *
   * Both variants are normalized to upper-case keys so callers can
   * match against `REPLAYGAIN_TRACK_GAIN` etc. without worrying about
   * the encoder's casing.
   */
  private fun extractKeyValue(entry: Metadata.Entry): Pair<String, String>? = when (entry) {
    is TextInformationFrame -> {
      // For TXXX frames (the user-defined text frame), Media3 sets
      // `description` to the user-defined key. Other ID3 text frames
      // (TIT2, TPE1, ...) have a fixed semantic description that
      // is never "REPLAYGAIN_*", so this filter is naturally tight.
      val key = entry.description
      val value = entry.values.firstOrNull() ?: entry.value
      if (key.isNullOrBlank() || value.isNullOrBlank()) null
      else key.uppercase() to value
    }
    is VorbisComment -> entry.key.uppercase() to entry.value
    else -> null
  }

  companion object {
    private const val TAG = "tonearm-replaygain"
    private const val RETRIEVE_TIMEOUT_MS = 1500L
  }
}

/**
 * Parsed ReplayGain values for one media file. All fields are nullable
 * — a real-world file commonly has track values but no album values
 * (or vice versa), or none at all.
 */
data class ReplayGainTags(
  val trackGainDb: Float? = null,
  val trackPeak: Float? = null,
  val albumGainDb: Float? = null,
  val albumPeak: Float? = null,
) {
  companion object {
    val Empty = ReplayGainTags()
  }
}
