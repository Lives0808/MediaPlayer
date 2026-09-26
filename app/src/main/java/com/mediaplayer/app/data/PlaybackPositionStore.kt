package com.mediaplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 记录每个视频的播放进度，用于"继续播放"。
 * 使用 stringSet 以 "id:position:timestamp" 的形式存储，时间戳用于超出上限时按最近保存淘汰。
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
                .toSet()
            val updated = if (position > 0L) {
                current + "$videoId:$position:${System.currentTimeMillis()}"
            } else {
                current
            }
            prefs[KEY_POSITIONS] = if (updated.size > MAX_ENTRIES) {
                // stringSet 不保留顺序，takeLast 拿到的是任意条目；按时间戳保留最近保存的
                updated.sortedByDescending(::entryTimestamp).take(MAX_ENTRIES).toSet()
            } else {
                updated
            }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_POSITIONS) }
    }

    /** 旧格式 "id:position" 没有时间戳，视为最旧，优先被淘汰 */
    private fun entryTimestamp(entry: String): Long {
        val parts = entry.split(':')
        return if (parts.size >= 3) parts[2].toLongOrNull() ?: 0L else 0L
    }

    private companion object {
        val KEY_POSITIONS = stringSetPreferencesKey("playback_positions")
        const val MAX_ENTRIES = 300
    }
}
