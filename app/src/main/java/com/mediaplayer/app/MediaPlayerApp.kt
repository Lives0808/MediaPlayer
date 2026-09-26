package com.mediaplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.mediaplayer.app.core.AppContainer

/**
 * 应用入口。持有全局单例容器（简易依赖注入），
 * 并为 Coil 注册视频帧解码器，用于列表缩略图。
 */
class MediaPlayerApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
