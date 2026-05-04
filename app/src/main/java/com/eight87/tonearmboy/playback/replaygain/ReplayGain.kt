package com.eight87.tonearmboy.playback.replaygain

import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy

/**
 * Pure-function helpers for Phase D.9b.1 / D.9b.2 ReplayGain.
 *
 * The pipeline is intentionally split into three small, individually
 * testable pieces:
 *
 *  1. [ReplayGainParser.parseGainDb] turns a raw tag value (e.g.
 *     `"-6.34 dB"`, `"+1.20"`, `"0"`) into a normalized dB float.
 *  2. [computeGain] picks track-vs-album gain per the user's strategy
 *     and the queue's album coverage.
 *  3. [linearGainFromDb] converts the resulting dB total to a linear
 *     scalar suitable for `Player.setVolume`, clamped to [0.0, 1.0].
 *
 * Splitting these keeps the player wiring testable end-to-end without
 * spinning a real ExoPlayer.
 */
object ReplayGainParser {

  /**
   * Parse a ReplayGain `*_GAIN` tag value into dB. The standard format
   * is `"-6.34 dB"`, but real-world files include
   *
   *   - `"-6.34"` (no unit suffix)
   *   - `"+1.2 dB"` (explicit positive sign)
   *   - `"0"` (no decimal)
   *   - `"  -6.34 dB  "` (leading / trailing whitespace)
   *   - locale-using-comma-as-decimal: `"-6,34 dB"`
   *
   * Returns null when the input is null, blank, or doesn't contain a
   * number we can parse.
   */
  fun parseGainDb(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim()
      .removeSuffix("dB").removeSuffix("DB").removeSuffix("db")
      .trim()
      // Some encoders write a comma decimal separator regardless of
      // locale. Normalize to dot before Float.parseFloat.
      .replace(',', '.')
    return cleaned.toFloatOrNull()
  }

  /**
   * Parse a ReplayGain `*_PEAK` tag. Peaks are linear, no dB suffix,
   * commonly look like `"0.987654"` or `"1.012345"`. Same lenient
   * trimming as [parseGainDb] but no `dB` suffix to strip.
   */
  fun parsePeak(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    return raw.trim().replace(',', '.').toFloatOrNull()
  }
}

/**
 * Decide the dB gain to apply to the currently-playing track given the
 * user's [strategy], the parsed track + album gains, and how much of the
 * surrounding album is queued.
 *
 *  - `Off` always returns 0 dB
 *  - `Track` returns the track gain (or 0 dB when the track tag is
 *    missing — an opt-in normalization should never amplify)
 *  - `Album` returns the album gain
 *  - `Smart` returns the album gain when [queueAlbumCoverage] >= 0.75
 *    (the queue is "playing the album"), otherwise the track gain.
 *
 * Visible for tests at [com.eight87.tonearmboy.playback.replaygain.ReplayGainStrategyTest].
 */
fun computeGain(
  strategy: ReplayGainStrategy,
  trackGainDb: Float?,
  albumGainDb: Float?,
  queueAlbumCoverage: Float,
): Float = when (strategy) {
  ReplayGainStrategy.Off -> 0f
  ReplayGainStrategy.Track -> trackGainDb ?: 0f
  ReplayGainStrategy.Album -> albumGainDb ?: 0f
  ReplayGainStrategy.Smart -> {
    val playingFullAlbum = queueAlbumCoverage >= SMART_THRESHOLD
    if (playingFullAlbum) (albumGainDb ?: trackGainDb ?: 0f)
    else (trackGainDb ?: albumGainDb ?: 0f)
  }
}

/**
 * Convert a dB gain to the [0.0, 1.0] linear scalar accepted by
 * `Player.setVolume`.
 *
 * Math: linear = 10^(dB / 20). The result is clamped at the upper end
 * to 1.0 because Media3's volume parameter does not amplify above
 * unity gain — adding +N dB above the file's natural level would
 * require an `AudioProcessor` chain we deliberately do not ship in v1.
 *
 * Negative dB values map to 0.0..1.0 and attenuate as expected;
 * positive values clamp to 1.0 and we document this constraint in the
 * settings UI subtitle ("attenuates only").
 */
fun linearGainFromDb(dB: Float): Float {
  val linear = Math.pow(10.0, dB.toDouble() / 20.0).toFloat()
  return linear.coerceIn(0f, 1f)
}

/**
 * Smart-mode threshold: if the user has queued >=75% of an album's
 * tracks consecutively, treat it as "playing the album" and use
 * album gain. Below that, track gain. Visible for tests.
 */
internal const val SMART_THRESHOLD: Float = 0.75f
