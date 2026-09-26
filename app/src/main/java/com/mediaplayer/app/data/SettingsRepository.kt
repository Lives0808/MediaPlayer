package com.mediaplayer.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "media_player_prefs")

/** 播放页的画面比例选项，取值对应 AspectRatioFrameLayout.RESIZE_MODE_* */
object ResizeModes {
    const val FIT = 0
    const val FIXED_WIDTH = 1
    const val FIXED_HEIGHT = 2
    const val FILL = 3
    const val ZOOM = 4
}

data class PlayerSettings(
    val autoLandscape: Boolean = true,
    val rememberPosition: Boolean = true,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val defaultResizeMode: Int = ResizeModes.FIT,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        PlayerSettings(
            autoLandscape = prefs[KEY_AUTO_LANDSCAPE] ?: true,
            rememberPosition = prefs[KEY_REMEMBER_POSITION] ?: true,
            backgroundPlay = prefs[KEY_BACKGROUND_PLAY] ?: true,
            autoPip = prefs[KEY_AUTO_PIP] ?: false,
            defaultSpeed = prefs[KEY_DEFAULT_SPEED] ?: 1.0f,
            defaultResizeMode = prefs[KEY_RESIZE_MODE] ?: ResizeModes.FIT,
        )
    }

    suspend fun setAutoLandscape(value: Boolean) = edit { it[KEY_AUTO_LANDSCAPE] = value }
    suspend fun setRememberPosition(value: Boolean) = edit { it[KEY_REMEMBER_POSITION] = value }
    suspend fun setBackgroundPlay(value: Boolean) = edit { it[KEY_BACKGROUND_PLAY] = value }
    suspend fun setAutoPip(value: Boolean) = edit { it[KEY_AUTO_PIP] = value }
    suspend fun setDefaultSpeed(value: Float) = edit { it[KEY_DEFAULT_SPEED] = value }
    suspend fun setDefaultResizeMode(value: Int) = edit { it[KEY_RESIZE_MODE] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_AUTO_LANDSCAPE = booleanPreferencesKey("auto_landscape")
        val KEY_REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val KEY_BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
        val KEY_DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val KEY_RESIZE_MODE = intPreferencesKey("resize_mode")
    }
}
