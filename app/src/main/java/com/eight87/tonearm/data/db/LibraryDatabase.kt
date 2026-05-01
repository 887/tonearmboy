package com.eight87.tonearm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
  version = 1,
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
          .build()
        instance = created
        return created
      }
    }
  }
}
