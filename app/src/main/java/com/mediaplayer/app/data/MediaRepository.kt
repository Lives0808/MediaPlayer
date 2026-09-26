package com.mediaplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 MediaStore 读取本机视频。
 */
class MediaRepository(private val context: Context) {

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    suspend fun loadVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATA)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val videos = mutableListOf<VideoItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeIndex = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val dataIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val widthIndex = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightIndex = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val relativeIndex = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val absolutePath = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                    val relativePath = if (relativeIndex >= 0) cursor.getString(relativeIndex).orEmpty() else ""
                    val folder = when {
                        relativePath.isNotBlank() -> relativePath
                        absolutePath.isNotBlank() -> absolutePath.substringBeforeLast('/', "")
                        else -> ""
                    }
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                    videos += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = name.ifBlank { "未知视频" },
                        duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L,
                        size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                        dateAdded = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                        folderPath = folder,
                        width = if (widthIndex >= 0) cursor.getInt(widthIndex) else 0,
                        height = if (heightIndex >= 0) cursor.getInt(heightIndex) else 0,
                        mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else "",
                    )
                }
            }
        }
        videos
    }
}
