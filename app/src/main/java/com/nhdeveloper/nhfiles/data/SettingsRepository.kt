package com.nhdeveloper.nhfiles.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "nhfiles_settings")

object K {
    val THEME = intPreferencesKey("theme")
    val START = stringPreferencesKey("start_dir")
    val EXT = booleanPreferencesKey("show_ext")
    val HIDDEN = booleanPreferencesKey("show_hidden")
    val SORT = intPreferencesKey("sort_by")
    val ASC = booleanPreferencesKey("sort_asc")
    val GRID = booleanPreferencesKey("grid")
    val CONFIRM = booleanPreferencesKey("confirm_delete")
    val KEEP = booleanPreferencesKey("keep_recents")
    val BOOKMARKS = stringPreferencesKey("bookmarks")
    val RECENTS = stringPreferencesKey("recents")
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                theme = p[K.THEME] ?: 0,
                startDir = p[K.START] ?: "",
                showExtensions = p[K.EXT] ?: true,
                showHidden = p[K.HIDDEN] ?: false,
                sortBy = SortBy.values().getOrElse(p[K.SORT] ?: 0) { SortBy.NAME },
                sortAsc = p[K.ASC] ?: true,
                grid = p[K.GRID] ?: false,
                confirmDelete = p[K.CONFIRM] ?: true,
                keepRecents = p[K.KEEP] ?: true,
                bookmarks = splitList(p[K.BOOKMARKS]),
                recents = splitList(p[K.RECENTS]),
            )
        }

    suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { prefs -> block(prefs) }
    }

    companion object {
        fun splitList(s: String?): List<String> =
            (s ?: "").split('\n').filter { it.isNotBlank() }
    }
}
