package com.eight87.tonearmboy.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearmboy.data.model.Track

/**
 * R.C.3 — concrete [QueueCommands] wrapping a `MediaController`.
 *
 * Mirrors [MediaTransportCommands] in shape: live controller via
 * [controllerProvider], post-mutation push via [projector].
 */
@UnstableApi
internal class MediaQueueCommands(
  private val controllerProvider: () -> MediaController?,
  private val projector: PlaybackStateProjector,
) : QueueCommands {

  override fun addToQueue(track: Track) {
    val ctl = controllerProvider() ?: return
    val item = track.toMediaItem()
    if (ctl.mediaItemCount == 0) {
      ctl.setMediaItem(item)
      ctl.prepare()
      ctl.play()
    } else {
      ctl.addMediaItem(item)
    }
    projector.pushAll()
  }

  override fun seekToQueueIndex(index: Int) {
    val ctl = controllerProvider() ?: return
    if (index < 0 || index >= ctl.mediaItemCount) return
    ctl.seekTo(index, 0L)
    ctl.play()
    projector.pushAll()
  }

  override fun removeQueueItem(index: Int) {
    val ctl = controllerProvider() ?: return
    if (index < 0 || index >= ctl.mediaItemCount) return
    ctl.removeMediaItem(index)
    projector.pushAll()
  }

  override fun removeQueueItemsByMediaIds(deletedMediaIds: Set<String>): Int {
    val ctl = controllerProvider() ?: return 0
    if (deletedMediaIds.isEmpty()) return 0
    val queueIds = (0 until ctl.mediaItemCount).map { i -> ctl.getMediaItemAt(i).mediaId }
    val toRemove = queueIndicesToRemove(queueIds, deletedMediaIds)
    if (toRemove.isEmpty()) return 0
    // Indices already arrive in descending order so each removeMediaItem
    // call doesn't shift the indices we still need to act on.
    for (idx in toRemove) ctl.removeMediaItem(idx)
    if (ctl.mediaItemCount == 0) {
      ctl.stop()
      ctl.clearMediaItems()
    }
    projector.pushAll()
    return toRemove.size
  }

  override fun moveQueueItem(from: Int, to: Int) {
    val ctl = controllerProvider() ?: return
    val n = ctl.mediaItemCount
    if (from < 0 || from >= n || to < 0 || to >= n || from == to) return
    ctl.moveMediaItem(from, to)
    projector.pushAll()
  }
}
