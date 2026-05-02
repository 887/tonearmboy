package com.eight87.tonearm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    TrackEntity::class,
    TrackFts::class,
    AlbumEntity::class,
    ArtistEntity::class,
    GenreEntity::class,
    PlaylistEntity::class,
    PlaylistTrackEntity::class,
  ],
  version = 2,
  exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
  abstract fun trackDao(): TrackDao
  abstract fun albumDao(): AlbumDao
  abstract fun artistDao(): ArtistDao
  abstract fun genreDao(): GenreDao
  abstract fun playlistDao(): PlaylistDao
  abstract fun libraryDao(): LibraryDao

  companion object {
    private const val DB_NAME = "tonearm-library.db"

    /**
     * v1 -> v2: add ReplayGain columns introduced in Phase D.9b.1.
     *
     *  - `tracks.replayGainTrackDb` / `replayGainTrackPeak` from the
     *    `REPLAYGAIN_TRACK_GAIN` / `REPLAYGAIN_TRACK_PEAK` Vorbis or
     *    ID3v2 TXXX frame
     *  - `albums.replayGainAlbumDb` / `replayGainAlbumPeak` from
     *    `REPLAYGAIN_ALBUM_GAIN` / `REPLAYGAIN_ALBUM_PEAK`
     *
     * Existing rows get NULL for the new columns; the next library
     * scan will fill them in. We avoid `fallbackToDestructiveMigration`
     * so playlists and the user's persisted queue survive the upgrade.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN replayGainTrackDb REAL")
        db.execSQL("ALTER TABLE tracks ADD COLUMN replayGainTrackPeak REAL")
        db.execSQL("ALTER TABLE tracks ADD COLUMN mediaStoreAlbumId INTEGER")
        db.execSQL("ALTER TABLE albums ADD COLUMN replayGainAlbumDb REAL")
        db.execSQL("ALTER TABLE albums ADD COLUMN replayGainAlbumPeak REAL")
        db.execSQL("ALTER TABLE albums ADD COLUMN mediaStoreAlbumId INTEGER")
      }
    }

    @Volatile private var instance: LibraryDatabase? = null

    /**
     * Process-wide [LibraryDatabase] singleton. The repository owns the
     * lifecycle; tests construct in-memory variants directly via
     * [Room.inMemoryDatabaseBuilder].
     */
    fun get(context: Context): LibraryDatabase {
      val existing = instance
      if (existing != null) return existing
      synchronized(this) {
        val maybe = instance
        if (maybe != null) return maybe
        val created = Room.databaseBuilder(
          context.applicationContext,
          LibraryDatabase::class.java,
          DB_NAME,
        )
          // No fallbackToDestructiveMigration: schema is exported, so
          // we'll write proper migrations as the schema evolves.
          .addMigrations(MIGRATION_1_2)
          .build()
        instance = created
        return created
      }
    }
  }
}
