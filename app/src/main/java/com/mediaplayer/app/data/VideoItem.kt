package com.mediaplayer.app.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * 媒体库中的一个视频条目。
 */
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val folderPath: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
) {
    /** 用于界面展示的文件夹名 */
    val folderName: String
        get() = folderPath.trimEnd('/').substringAfterLast('/').ifBlank { "内部存储" }

    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width} × ${height}" else "未知分辨率"

    val hasSize: Boolean get() = width > 0 && height > 0

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()
}
