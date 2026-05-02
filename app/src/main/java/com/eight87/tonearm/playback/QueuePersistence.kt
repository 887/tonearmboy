package com.eight87.tonearm.playback

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Phase E.5 — persist the current queue + playback position so the
 * service can rebuild state across process death and after the OS
 * reaps the foreground service.
 *
 * Storage: a small DataStore Preferences file. The queue is a single
 * JSON-encoded string; the rest are scalar prefs. Writes are
 * idempotent and cheap — a position write is ~30 bytes through
 * DataStore's atomic file replace, and we debounce position saves
 * from `PlaybackService` so we are not hitting the disk every 250 ms.
 *
 * What we persist per item: media id, source URI, title, artist,
 * album, album artist, artwork URI. That is the round-trip the
 * `onPlaybackResumption` callback hands back to Media3 as a
 * [MediaSession.MediaItemsWithStartPosition].
 *
 * We deliberately do NOT persist position more often than ~2 s —
 * `PlaybackService` is responsible for the debounce; this class is a
 * dumb store.
 */
class QueuePersistence(private val context: Context) {

  /** A single queue item, serialised as JSON inside the prefs string. */
  @Serializable
  data class Entry(
    val mediaId: String,
    val uri: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val artworkUri: String? = null,
  )

  /** A snapshot of everything we restore on reconnect. */
  data class Snapshot(
    val items: List<Entry>,
    val startIndex: Int,
    val startPositionMs: Long,
  ) {
    fun isEmpty(): Boolean = items.isEmpty()

    /** Convert to the Media3 type the resumption callback returns. */
    fun toMediaItemsWithStartPosition(): MediaSession.MediaItemsWithStartPosition =
      MediaSession.MediaItemsWithStartPosition(
        items.map { it.toMediaItem() },
        startIndex.coerceAtLeast(0),
        startPositionMs.coerceAtLeast(0),
      )

    companion object {
      val Empty = Snapshot(emptyList(), 0, 0L)
    }
  }

  /** Replace the full queue. Position resets to (index, 0). */
  suspend fun saveQueue(items: List<Entry>, startIndex: Int) {
    val json = JSON.encodeToString(LIST_SERIALIZER, items)
    context.tonearmPlaybackDataStore.edit { prefs ->
      prefs[KEY_QUEUE_JSON] = json
      prefs[KEY_INDEX] = startIndex.coerceAtLeast(0)
      prefs[KEY_POSITION] = 0L
    }
  }

  /** Update only the (index, position) tuple — called on tick. */
  suspend fun savePosition(index: Int, positionMs: Long) {
    context.tonearmPlaybackDataStore.edit { prefs ->
      prefs[KEY_INDEX] = index.coerceAtLeast(0)
      prefs[KEY_POSITION] = positionMs.coerceAtLeast(0)
    }
  }

  /** Drop the persisted queue entirely. */
  suspend fun clear() {
    context.tonearmPlaybackDataStore.edit { it.clear() }
  }

  /** One-shot load, used by the resumption callback. */
  suspend fun load(): Snapshot {
    val prefs = context.tonearmPlaybackDataStore.data.first()
    val raw = prefs[KEY_QUEUE_JSON] ?: return Snapshot.Empty
    val items = runCatching {
      JSON.decodeFromString(LIST_SERIALIZER, raw)
    }.getOrDefault(emptyList())
    if (items.isEmpty()) return Snapshot.Empty
    return Snapshot(
      items = items,
      startIndex = prefs[KEY_INDEX] ?: 0,
      startPositionMs = prefs[KEY_POSITION] ?: 0L,
    )
  }

  companion object {
    internal val KEY_QUEUE_JSON = stringPreferencesKey("queue_json")
    internal val KEY_INDEX = intPreferencesKey("queue_index")
    internal val KEY_POSITION = longPreferencesKey("queue_position_ms")

    internal val JSON = Json {
      ignoreUnknownKeys = true
      encodeDefaults = false
    }
    internal val LIST_SERIALIZER = ListSerializer(Entry.serializer())

    /** Minimum ms between persisted position updates while playing. */
    const val POSITION_DEBOUNCE_MS: Long = 2_000L

    /**
     * Convert a Media3 [MediaItem] into the persisted [Entry]. Only
     * the fields required to rehydrate playback are kept.
     */
    fun fromMediaItem(item: MediaItem): Entry {
      val md = item.mediaMetadata
      val uri = item.localConfiguration?.uri?.toString().orEmpty()
      return Entry(
        mediaId = item.mediaId,
        uri = uri,
        title = md.title?.toString(),
        artist = md.artist?.toString(),
        album = md.albumTitle?.toString(),
        albumArtist = md.albumArtist?.toString(),
        artworkUri = md.artworkUri?.toString(),
      )
    }
  }
}

/** Serialisation helper — used by the companion's [Snapshot.toMediaItemsWithStartPosition]. */
internal fun QueuePersistence.Entry.toMediaItem(): MediaItem {
  val metadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist)
    .setAlbumTitle(album)
    .setAlbumArtist(albumArtist)
    .also { if (artworkUri != null) it.setArtworkUri(Uri.parse(artworkUri)) }
    .build()
  return MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(uri)
    .setMediaMetadata(metadata)
    .build()
}

internal val Context.tonearmPlaybackDataStore: DataStore<Preferences> by preferencesDataStore(name = "tonearm_playback")
