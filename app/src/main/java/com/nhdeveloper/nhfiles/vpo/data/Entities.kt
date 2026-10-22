package com.nhdeveloper.nhfiles.vpo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val position: Long,
    val duration: Long,
    val lastPlayed: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val uri: String, val addedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_items", indices = [Index("playlistId")])
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val addedAt: Long
)

data class PlaylistRow(val id: Long, val name: String, val itemCount: Int)
