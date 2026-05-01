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
  )

  fun deriveAlbums(tracks: List<TrackEntity>): List<AlbumEntity> {
    val seen = HashMap<Pair<String, String?>, AlbumEntity>()
    for (t in tracks) {
      val name = t.album ?: continue
      val artist = t.albumArtist ?: t.artist
      val key = name to artist
      val existing = seen[key]
      if (existing == null) {
        seen[key] = AlbumEntity(name = name, artist = artist, year = t.year)
      } else if (existing.year == null && t.year != null) {
        seen[key] = existing.copy(year = t.year)
      }
    }
    return seen.values.toList()
  }

  fun deriveArtists(tracks: List<TrackEntity>): List<ArtistEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) {
      (t.albumArtist ?: t.artist)?.let { names += it }
    }
    return names.map { ArtistEntity(name = it) }
  }

  fun deriveGenres(tracks: List<TrackEntity>): List<GenreEntity> {
    val names = LinkedHashSet<String>()
    for (t in tracks) t.genre?.let { names += it }
    return names.map { GenreEntity(name = it) }
  }
}
