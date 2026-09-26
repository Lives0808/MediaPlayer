package com.mediaplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 记录每个视频的播放进度，用于"继续播放"。
 * 使用 stringSet 以 "id:position" 的形式存储。
 */
class PlaybackPositionStore(private val context: Context) {

    val positions: Flow<Map<Long, Long>> = context.dataStore.data.map { prefs ->
        prefs[KEY_POSITIONS].orEmpty().mapNotNull { entry ->
            val parts = entry.split(':')
            val id = parts.getOrNull(0)?.toLongOrNull()
            val position = parts.getOrNull(1)?.toLongOrNull()
            if (id != null && position != null) id to position else null
        }.toMap()
    }

    suspend fun save(videoId: Long, position: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_POSITIONS].orEmpty()
                .filterNot { it.substringBefore(':').toLongOrNull() == videoId }
                .toMutableSet()
            if (position > 0L) {
                current += "$videoId:$position"
            }
            prefs[KEY_POSITIONS] = if (current.size > MAX_ENTRIES) {
                current.toList().takeLast(MAX_ENTRIES).toSet()
            } else {
                current
            }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_POSITIONS) }
    }

    private companion object {
        val KEY_POSITIONS = stringSetPreferencesKey("playback_positions")
        const val MAX_ENTRIES = 300
    }
}
