package com.nhdeveloper.nhfiles.vpo.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM history ORDER BY lastPlayed DESC")
    fun history(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE uri = :uri")
    suspend fun historyFor(uri: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(h: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("DELETE FROM history WHERE uri = :uri")
    suspend fun deleteHistory(uri: String)

    @Query("SELECT uri FROM favorites ORDER BY addedAt DESC")
    fun favoriteUris(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun removeFavorite(uri: String)

    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount FROM playlists p ORDER BY p.createdAt DESC")
    fun playlists(): Flow<List<PlaylistRow>>

    @Insert
    suspend fun addPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    suspend fun deletePlaylistItems(id: Long)

    @Query("SELECT uri FROM playlist_items WHERE playlistId = :id ORDER BY addedAt, id")
    fun playlistUris(id: Long): Flow<List<String>>

    @Insert
    suspend fun addPlaylistItem(i: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND uri = :uri")
    suspend fun removePlaylistItem(id: Long, uri: String)

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :id AND uri = :uri")
    suspend fun countInPlaylist(id: Long, uri: String): Int

    @Query("SELECT id, name, 0 AS itemCount FROM playlists ORDER BY createdAt DESC")
    suspend fun playlistsOnce(): List<PlaylistRow>
}

@Database(
    entities = [HistoryEntity::class, FavoriteEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vpo.db")
                .build().also { instance = it }
        }
    }
}
