package com.eight87.tonearm.data

import com.eight87.tonearm.data.db.AlbumEntity
import com.eight87.tonearm.data.db.ArtistEntity
import com.eight87.tonearm.data.db.GenreEntity
import com.eight87.tonearm.data.db.TrackEntity
import com.eight87.tonearm.data.model.Track

/**
 * Pure functions translating between the on-disk Room entities and the
 * domain models the rest of the app sees.
 */
internal object Mapping {

  fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    data = data,
    dateAddedSeconds = dateAddedSeconds,
    replayGainTrackDb = replayGainTrackDb,
    replayGainTrackPeak = replayGainTrackPeak,
    mediaStoreAlbumId = mediaStoreAlbumId,
    // album-level ReplayGain fields are populated by the repository
    // after the album rollup is rebuilt; the per-track entity itself
    // only stores track-level values to keep the schema minimal.
  )

  fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    data = data,
    dateAddedSeconds = dateAddedSeconds,
    replayGainTrackDb = replayGainTrackDb,
    replayGainTrackPeak = replayGainTrackPeak,
    mediaStoreAlbumId = mediaStoreAlbumId,
  )

  fun deriveAlbums(tracks: List<TrackEntity>): List<AlbumEntity> {
    val seen = HashMap<Pair<String, String?>, AlbumEntity>()
    for (t in tracks) {
      val name = t.album ?: continue
      val artist = t.albumArtist ?: t.artist
      val key = name to artist
      val existing = seen[key]
      if (existing == null) {
        seen[key] = AlbumEntity(
          name = name,
          artist = artist,
          year = t.year,
          mediaStoreAlbumId = t.mediaStoreAlbumId,
        )
      } else {
        var next = existing
        if (next.year == null && t.year != null) next = next.copy(year = t.year)
        if (next.mediaStoreAlbumId == null && t.mediaStoreAlbumId != null) {
          next = next.copy(mediaStoreAlbumId = t.mediaStoreAlbumId)
        }
        if (next !== existing) seen[key] = next
      }
    }
    return seen.values.toList()
  }

  /**
   * D.9b.1 — fold per-track album ReplayGain reads into the album
   * rollup. Tracks carry the album-level dB/peak from their own
   * `REPLAYGAIN_ALBUM_*` tags; this picks the first non-null value
   * for each (album, artist) key and merges it into the existing
   * `AlbumEntity` list returned by [deriveAlbums]. Tracks without
   * `album` are ignored (they cannot belong to any album row).
   */
  fun foldAlbumReplayGain(
    albums: List<AlbumEntity>,
    tracksWithAlbumGain: List<Pair<TrackEntity, Pair<Float?, Float?>>>,
  ): List<AlbumEntity> {
    if (albums.isEmpty()) return albums
    val byKey: Map<Pair<String, String?>, Pair<Float?, Float?>> = buildMap {
      for ((t, gainPeak) in tracksWithAlbumGain) {
        val name = t.album ?: continue
        val artist = t.albumArtist ?: t.artist
        val key = name to artist
        val (gain, peak) = gainPeak
        val existing = get(key)
        if (existing == null) {
          put(key, gain to peak)
        } else {
          put(
            key,
            (existing.first ?: gain) to (existing.second ?: peak),
          )
        }
      }
    }
    return albums.map { a ->
      val gp = byKey[a.name to a.artist]
      if (gp == null) a
      else a.copy(
        replayGainAlbumDb = a.replayGainAlbumDb ?: gp.first,
        replayGainAlbumPeak = a.replayGainAlbumPeak ?: gp.second,
      )
    }
  }

  fun deriveArtists(tracks: List<TrackEntity>): List<ArtistEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) {
      (t.albumArtist ?: t.artist)?.let { names += it }
    }
    return names.map { ArtistEntity(name = it) }
  }

  /**
   * D.9c.1 — derive artist rows from the live scan rather than from
   * the cached `TrackEntity` set, so the per-track `additionalArtists` /
   * `additionalAlbumArtists` lists produced by the multi-value splitter
   * contribute extra rows. Falls back to the entity-only behaviour for
   * tracks with no additional values.
   */
  fun deriveArtistsFromDomain(tracks: List<Track>): List<ArtistEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) {
      // Prefer album-artist when present; else artist. Splits are
      // already applied — the primary value is in `albumArtist`/`artist`,
      // additional values in the parallel lists.
      val primary = t.albumArtist ?: t.artist
      primary?.takeIf { it.isNotBlank() }?.let { names += it }
      val additional = if (t.albumArtist != null) {
        t.additionalAlbumArtists
      } else {
        t.additionalArtists
      }
      for (a in additional) if (a.isNotBlank()) names += a
    }
    return names.map { ArtistEntity(name = it) }
  }

  fun deriveGenres(tracks: List<TrackEntity>): List<GenreEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) t.genre?.let { names += it }
    return names.map { GenreEntity(name = it) }
  }

  /**
   * D.9c.1 — derive genre rows from the live scan, including each
   * track's `additionalGenres` from the multi-value splitter.
   */
  fun deriveGenresFromDomain(tracks: List<Track>): List<GenreEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) {
      t.genre?.takeIf { it.isNotBlank() }?.let { names += it }
      for (g in t.additionalGenres) if (g.isNotBlank()) names += g
    }
    return names.map { GenreEntity(name = it) }
  }
}
