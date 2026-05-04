package com.eight87.tonearmboy.data

import com.eight87.tonearmboy.data.db.AlbumEntity
import com.eight87.tonearmboy.data.db.ArtistEntity
import com.eight87.tonearmboy.data.db.GenreEntity
import com.eight87.tonearmboy.data.db.TrackEntity
import com.eight87.tonearmboy.data.model.ScannedTrack
import com.eight87.tonearmboy.data.model.Track

/**
 * Pure functions translating between the on-disk Room entities and the
 * domain models the rest of the app sees.
 *
 * R.F.4 — `ScannedTrack` is the scanner's superset; `Track` is the
 * cache-faithful subset. `toDomain` produces `Track` from `TrackEntity`
 * 1:1 (no defaults, no drift); the scan flow uses
 * [ScannedTrack.toTrackEntity] to drop the scan-only fields when
 * persisting.
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

  /** R.F.4 — explicit lossy conversion: drops the album-level RG + splitter outputs. */
  fun ScannedTrack.toTrackEntity(): TrackEntity = TrackEntity(
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
   * D.9c.1 — derive artist rows from the live scan, including each
   * track's `additionalArtists` / `additionalAlbumArtists` lists from
   * the multi-value splitter. Replaces the pre-R.F.4
   * `deriveArtistsFromDomain(List<Track>)`. (Data-F4.)
   */
  fun deriveArtistsFromScanned(tracks: List<ScannedTrack>): List<ArtistEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) {
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
   * track's `additionalGenres` from the multi-value splitter. Replaces
   * the pre-R.F.4 `deriveGenresFromDomain(List<Track>)`. (Data-F4.)
   */
  fun deriveGenresFromScanned(tracks: List<ScannedTrack>): List<GenreEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) {
      t.genre?.takeIf { it.isNotBlank() }?.let { names += it }
      for (g in t.additionalGenres) if (g.isNotBlank()) names += g
    }
    return names.map { GenreEntity(name = it) }
  }
}
