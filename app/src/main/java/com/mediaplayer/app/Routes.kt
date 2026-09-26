package com.mediaplayer.app

import android.content.Intent
import android.net.Uri

/** 应用内导航路由 */
object Routes {

    const val HOME = "home"
    const val SETTINGS = "settings"

    /** player/{mediaId}，mediaId 为视频 id 或 external_xxx */
    const val PLAYER = "player/{mediaId}"

    fun player(videoId: Long): String = "player/$videoId"

    fun externalPlayer(): String = "player/${EXTERNAL_PREFIX}${System.currentTimeMillis()}"

    const val MEDIA_ID_ARG = "mediaId"
    const val EXTERNAL_PREFIX = "external_"
}

/** 把外部传入的 Intent 解析成待播放的 URI */
fun Intent.toExternalVideoUri(): Uri? =
    if (action == Intent.ACTION_VIEW) data else null
