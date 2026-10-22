package com.nhdeveloper.nhfiles.vpo.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.nhfiles.vpo.data.*
import com.nhdeveloper.nhfiles.vpo.util.hasVideoPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(private val app: Application) : AndroidViewModel(app) {
    val dao = AppDatabase.get(app).dao()
    val settingsRepo = SettingsRepository(app)

    private val raw = MutableStateFlow<List<VideoItem>>(emptyList())
    val loading = MutableStateFlow(false)
    val scanned = MutableStateFlow(false)

    val settings: StateFlow<AppSettings> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val videos: StateFlow<List<VideoItem>> =
        combine(raw, settings.map { it.sort }.distinctUntilChanged()) { list, sort -> sortList(list, sort) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: Flow<List<FolderItem>> = videos.map { list ->
        list.groupBy { it.folderKey }.map { (k, v) ->
            FolderItem(k, v.first().folderName, v.first().folderPath, v.size)
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default)

    val favoriteSet: Flow<Set<String>> = dao.favoriteUris().map { it.toSet() }

    val favorites: Flow<List<VideoItem>> = combine(videos, dao.favoriteUris()) { v, uris ->
        val m = v.associateBy { it.uri }; uris.mapNotNull { m[it] }
    }

    val history: Flow<List<Pair<VideoItem, HistoryEntity>>> = combine(videos, dao.history()) { v, h ->
        val m = v.associateBy { it.uri }; h.mapNotNull { e -> m[e.uri]?.let { it to e } }
    }

    val continueWatching: Flow<List<ContinueItem>> = history.map { list ->
        list.filter { (v, e) -> e.position > 5_000 && e.duration > 0 && e.position < e.duration - 5_000 }
            .take(10).map { (v, e) -> ContinueItem(v, e.position, (e.position * 100 / e.duration).toInt()) }
    }

    val playlists: Flow<List<PlaylistRow>> = dao.playlists()

    fun playlistVideos(id: Long): Flow<List<VideoItem>> = combine(videos, dao.playlistUris(id)) { v, uris ->
        val m = v.associateBy { it.uri }; uris.mapNotNull { m[it] }
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            val trees = settingsRepo.current().trees
            val list = withContext(Dispatchers.IO) { MediaRepository.load(app, trees, hasVideoPermission(app)) }
            raw.value = list
            scanned.value = true
            loading.value = false
        }
    }

    fun toggleFavorite(v: VideoItem, isFav: Boolean) = viewModelScope.launch {
        if (isFav) dao.removeFavorite(v.uri) else dao.addFavorite(FavoriteEntity(v.uri, System.currentTimeMillis()))
    }

    fun createPlaylist(name: String, first: VideoItem?) = viewModelScope.launch {
        val id = dao.addPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
        if (first != null) dao.addPlaylistItem(PlaylistItemEntity(playlistId = id, uri = first.uri, addedAt = System.currentTimeMillis()))
    }

    fun addToPlaylist(id: Long, v: VideoItem) = viewModelScope.launch {
        if (dao.countInPlaylist(id, v.uri) == 0)
            dao.addPlaylistItem(PlaylistItemEntity(playlistId = id, uri = v.uri, addedAt = System.currentTimeMillis()))
    }

    fun removeFromPlaylist(id: Long, v: VideoItem) = viewModelScope.launch { dao.removePlaylistItem(id, v.uri) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { dao.renamePlaylist(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { dao.deletePlaylistItems(id); dao.deletePlaylist(id) }
    fun clearHistory() = viewModelScope.launch { dao.clearHistory() }
    fun removeHistory(v: VideoItem) = viewModelScope.launch { dao.deleteHistory(v.uri) }

    fun addTree(uri: Uri) = viewModelScope.launch { settingsRepo.addTree(uri.toString()); refresh() }
    fun removeTree(uri: String) = viewModelScope.launch { settingsRepo.removeTree(uri); refresh() }
    fun <T> setSetting(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) =
        viewModelScope.launch { settingsRepo.set(key, v) }

    suspend fun playlistsOnce(): List<PlaylistRow> = dao.playlistsOnce()

    companion object {
        fun sortList(list: List<VideoItem>, sort: String): List<VideoItem> = when (sort) {
            "name_asc" -> list.sortedBy { it.name.lowercase() }
            "name_desc" -> list.sortedByDescending { it.name.lowercase() }
            "date_asc" -> list.sortedBy { it.dateAdded }
            "dur_desc" -> list.sortedByDescending { it.durationMs }
            "dur_asc" -> list.sortedBy { it.durationMs }
            "size_desc" -> list.sortedByDescending { it.size }
            "size_asc" -> list.sortedBy { it.size }
            else -> list.sortedByDescending { it.dateAdded }
        }
        val SORT_KEYS = listOf("date_desc", "date_asc", "name_asc", "name_desc", "dur_desc", "dur_asc", "size_desc", "size_asc")
        val SORT_LABELS = listOf("Newest first", "Oldest first", "Name (A–Z)", "Name (Z–A)",
            "Longest first", "Shortest first", "Largest first", "Smallest first")
    }
}
