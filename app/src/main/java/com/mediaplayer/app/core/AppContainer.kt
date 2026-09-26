package com.mediaplayer.app.core

import android.app.Application
import android.net.Uri
import com.mediaplayer.app.data.MediaRepository
import com.mediaplayer.app.data.PlaybackPositionStore
import com.mediaplayer.app.data.SettingsRepository

/**
 * 极简依赖容器，避免引入 DI 框架。
 */
class AppContainer(val app: Application) {

    val mediaRepository: MediaRepository by lazy { MediaRepository(app) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(app) }
    val playbackPositionStore: PlaybackPositionStore by lazy { PlaybackPositionStore(app) }

    /** 由外部应用通过 ACTION_VIEW / ACTION_SEND 传入、尚未解析成媒体库条目的视频 */
    var externalVideoUri: Uri? = null
    var externalVideoTitle: String? = null
}
