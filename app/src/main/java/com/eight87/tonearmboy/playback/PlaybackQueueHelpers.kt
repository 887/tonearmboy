package com.eight87.tonearmboy.playback

import androidx.media3.common.Player
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary

/**
 * R.C.6 — pure helpers extracted from `PlaybackUiController`. Each
 * one is testable without spinning a real `MediaController` (which
 * needs a live `MediaSession`, expensive in Robolectric).
 */

/**
 * Pure decision helper for D.9a.3 — split out so the matrix is unit
 * testable without spinning a full ExoPlayer + MediaController.
 */
internal fun shouldPauseOnRepeatBoundary(reason: Int, pauseOnRepeat: Boolean): Boolean =
  pauseOnRepeat && reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

/**
 * Phase F.4 — pure helper exposing the list-walking logic for
 * `removeQueueItemsByMediaIds`. Returns the queue indices, in
 * descending order, that should be passed to
 * `MediaController.removeMediaItem` (descending so the caller can
 * remove sequentially without bookkeeping the index shift).
 *
 * Visible for tests so the matrix (currently-playing track removed,
 * tail item removed, all items removed, no items match) can be locked
 * down without spinning a real `MediaController`.
 */
internal fun queueIndicesToRemove(
  queueMediaIds: List<String>,
  deletedMediaIds: Set<String>,
): List<Int> {
  if (deletedMediaIds.isEmpty() || queueMediaIds.isEmpty()) return emptyList()
  val out = ArrayList<Int>(queueMediaIds.size)
  for (i in queueMediaIds.indices.reversed()) {
    if (queueMediaIds[i] in deletedMediaIds) out += i
  }
  return out
}

/**
 * Pure helper exposing the queue-building logic for D.9a.4. Splitting
 * it out keeps the strategy testable without spinning a real
 * `MediaController` (which can only run inside the Robolectric / device
 * activity).
 */
internal fun computePlayFromLibraryQueue(
  surroundingList: List<Track>,
  tappedIndex: Int,
  strategy: PlayFromLibrary,
  allSongs: List<Track> = surroundingList,
): Pair<List<Track>, Int> {
  if (tappedIndex !in surroundingList.indices) return emptyList<Track>() to 0
  val tapped = surroundingList[tappedIndex]
  return when (strategy) {
    PlayFromLibrary.AllSongs -> {
      val idx = allSongs.indexOfFirst { it.id == tapped.id }.let { if (it < 0) 0 else it }
      allSongs to idx
    }
    PlayFromLibrary.ItemOnly -> listOf(tapped) to 0
    PlayFromLibrary.CurrentFilter -> surroundingList to tappedIndex
  }
}

/**
 * Pure helper exposing the queue-building logic for D.9a.5.
 */
internal fun computePlayFromDetailQueue(
  surroundingList: List<Track>,
  tappedIndex: Int,
  strategy: PlayFromItemDetails,
): Pair<List<Track>, Int> {
  if (tappedIndex !in surroundingList.indices) return emptyList<Track>() to 0
  val tapped = surroundingList[tappedIndex]
  return when (strategy) {
    PlayFromItemDetails.ShownItem -> surroundingList to tappedIndex
    PlayFromItemDetails.Album -> {
      val albumKey = tapped.album
      val filtered = if (albumKey.isNullOrBlank()) listOf(tapped)
      else surroundingList.filter { it.album == albumKey }
      val idx = filtered.indexOfFirst { it.id == tapped.id }.let { if (it < 0) 0 else it }
      filtered to idx
    }
    PlayFromItemDetails.Artist -> {
      val artistKey = tapped.albumArtist?.takeIf { it.isNotBlank() } ?: tapped.artist
      val filtered = if (artistKey.isNullOrBlank()) listOf(tapped)
      else surroundingList.filter { (it.albumArtist ?: it.artist) == artistKey }
      val idx = filtered.indexOfFirst { it.id == tapped.id }.let { if (it < 0) 0 else it }
      filtered to idx
    }
  }
}
