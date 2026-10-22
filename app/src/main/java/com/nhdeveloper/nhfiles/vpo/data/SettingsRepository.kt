package com.nhdeveloper.nhfiles.vpo.data

import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val sort: String = "date_desc",
    val resume: Boolean = true,
    val autoPlayNext: Boolean = true,
    val bgAudio: Boolean = false,
    val autoPip: Boolean = false,
    val defaultSpeed: Float = 1f,
    val defaultAspect: Int = 0,
    val subtitleScale: Float = 1f,
    val subtitleColor: Int = Color.WHITE,
    val audioLang: String = "",
    val subLang: String = "",
    val trees: Set<String> = emptySet()
)

object Keys {
    val SORT = stringPreferencesKey("sort")
    val RESUME = booleanPreferencesKey("resume")
    val AUTONEXT = booleanPreferencesKey("autonext")
    val BGAUDIO = booleanPreferencesKey("bgaudio")
    val AUTOPIP = booleanPreferencesKey("autopip")
    val SPEED = floatPreferencesKey("speed")
    val ASPECT = intPreferencesKey("aspect")
    val SUBSCALE = floatPreferencesKey("subscale")
    val SUBCOLOR = intPreferencesKey("subcolor")
    val AUDIOLANG = stringPreferencesKey("audiolang")
    val SUBLANG = stringPreferencesKey("sublang")
    val TREES = stringSetPreferencesKey("trees")
}

class SettingsRepository(private val context: Context) {
    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            sort = p[Keys.SORT] ?: "date_desc",
            resume = p[Keys.RESUME] ?: true,
            autoPlayNext = p[Keys.AUTONEXT] ?: true,
            bgAudio = p[Keys.BGAUDIO] ?: false,
            autoPip = p[Keys.AUTOPIP] ?: false,
            defaultSpeed = p[Keys.SPEED] ?: 1f,
            defaultAspect = p[Keys.ASPECT] ?: 0,
            subtitleScale = p[Keys.SUBSCALE] ?: 1f,
            subtitleColor = p[Keys.SUBCOLOR] ?: Color.WHITE,
            audioLang = p[Keys.AUDIOLANG] ?: "",
            subLang = p[Keys.SUBLANG] ?: "",
            trees = p[Keys.TREES] ?: emptySet()
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun addTree(uri: String) {
        context.dataStore.edit { it[Keys.TREES] = (it[Keys.TREES] ?: emptySet()) + uri }
    }

    suspend fun removeTree(uri: String) {
        context.dataStore.edit { it[Keys.TREES] = (it[Keys.TREES] ?: emptySet()) - uri }
    }
}
