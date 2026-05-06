package com.eight87.tonearmboy.data.albumart

import com.eight87.tonearmboy.data.albumKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks which album keys currently have an in-flight cover-art web
 * request. Anything that calls [AlbumArtFetcher.fetch] (the bulk
 * worker, the per-album manual overflow actions, the future per-track
 * search) marks the key as in-flight on entry and unmarks on exit.
 *
 * The CoverArt composable subscribes to [inFlight] for the album it
 * renders so it can show a "fetching from web" indicator instead of
 * the empty-placeholder while the request is open. Without this the
 * user sees an indistinguishable empty state during the entire fetch
 * round-trip — they reported "I don't know if you're attempting to
 * download or if it's just empty."
 *
 * Process-scoped singleton; the registry doesn't survive a process
 * restart, which is fine — fetches are short-lived and re-attempting
 * after a kill is the correct recovery.
 */
object AlbumArtFetchRegistry {
  private val _inFlight = MutableStateFlow<Set<String>>(emptySet())

  /** Read-only view of the keys currently being fetched. */
  val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()

  /**
   * Mark [albumKey] as fetching, run [block], then unmark — even on
   * exception or cancellation. Callers should always go through this
   * helper rather than calling [start] / [end] manually so the unmark
   * doesn't leak when [block] throws.
   */
  suspend inline fun <T> withFetch(albumKey: String, block: () -> T): T {
    start(albumKey)
    try {
      return block()
    } finally {
      end(albumKey)
    }
  }

  fun start(albumKey: String) {
    _inFlight.update { it + albumKey }
  }

  fun end(albumKey: String) {
    _inFlight.update { it - albumKey }
  }

  /** Convenience for callers that have name + artist instead of a pre-computed key. */
  fun isFetching(albumName: String, albumArtist: String?): Boolean =
    albumKey(albumName, albumArtist) in _inFlight.value
}
