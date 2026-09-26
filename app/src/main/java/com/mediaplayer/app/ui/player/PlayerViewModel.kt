package com.mediaplayer.app.ui.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.app.MediaPlayerApp
import com.mediaplayer.app.Routes
import com.mediaplayer.app.core.AppContainer
import com.mediaplayer.app.data.PlayerSettings
import com.mediaplayer.app.data.ResizeModes
import com.mediaplayer.app.data.VideoItem
import com.mediaplayer.app.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class TrackOption(
    val id: String,
    val label: String,
    val selected: Boolean,
)

data class SpeedOption(val value: Float, val label: String)

data class PlayerUiState(
    val controllerReady: Boolean = false,
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val speed: Float = 1f,
    val resizeMode: Int = ResizeModes.FIT,
    val title: String = "",
    val folderName: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val playlistIndex: Int = 0,
    val playlistSize: Int = 0,
    val subtitles: List<TrackOption> = emptyList(),
    val audioTracks: List<TrackOption> = emptyList(),
    val selectedSubtitleId: String? = null,
    val selectedAudioId: String? = null,
    val errorMessage: String? = null,
    val autoPip: Boolean = false,
    val autoLandscape: Boolean = true,
    val settingsReady: Boolean = false,
)

@UnstableApi
class PlayerViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val app: Application = container.app
    private val mediaIdArg: String = savedStateHandle.get<String>(Routes.MEDIA_ID_ARG).orEmpty()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    @Volatile
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var settings = PlayerSettings()
    private var playlist: List<VideoItem> = emptyList()
    private var startIndex = 0
    private var startPosition = 0L
    private var savedPositions: Map<Long, Long> = emptyMap()
    private var trackLookup: Map<String, Pair<TrackGroup, Int>> = emptyMap()
    private var firstTransition = true

    val player: Player? get() = controller

    val speeds: List<SpeedOption> = listOf(
        0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f,
    ).map { SpeedOption(it, formatSpeedLabel(it)) }

    init {
        viewModelScope.launch {
            savedPositions = runCatching { container.playbackPositionStore.positions.first() }
                .getOrDefault(emptyMap())
            settings = runCatching { container.settingsRepository.settings.first() }
                .getOrDefault(PlayerSettings())
            _state.update {
                it.copy(
                    resizeMode = settings.defaultResizeMode,
                    speed = settings.defaultSpeed,
                    autoPip = settings.autoPip,
                    autoLandscape = settings.autoLandscape,
                    settingsReady = true,
                )
            }
            val allVideos = runCatching { container.mediaRepository.loadVideos() }.getOrDefault(emptyList())
            preparePlaylist(allVideos)
            connect()
        }
        startTicker()
    }

    // ---------------------------------------------------------------- 播放列表

    private fun preparePlaylist(allVideos: List<VideoItem>) {
        if (mediaIdArg.startsWith(Routes.EXTERNAL_PREFIX) || mediaIdArg.isBlank()) {
            val externalUri: Uri? = container.externalVideoUri
            playlist = if (externalUri != null) {
                listOf(
                    VideoItem(
                        id = EXTERNAL_ID,
                        uri = externalUri,
                        title = container.externalVideoTitle ?: "外部视频",
                        duration = 0L,
                        size = 0L,
                        dateAdded = 0L,
                        folderPath = "",
                        width = 0,
                        height = 0,
                        mimeType = "",
                    ),
                )
            } else {
                emptyList()
            }
            startIndex = 0
            startPosition = 0L
        } else {
            val targetId = mediaIdArg.toLongOrNull()
            val target = allVideos.firstOrNull { it.id == targetId }
            if (target == null) {
                playlist = emptyList()
            } else {
                // 同目录下的视频组成播放列表，便于"上一集/下一集"
                val siblings = allVideos.filter { it.folderPath == target.folderPath }
                playlist = siblings.ifEmpty { listOf(target) }
                startIndex = playlist.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
                startPosition = if (settings.rememberPosition) savedPositions[target.id] ?: 0L else 0L
            }
        }

        _state.update {
            it.copy(
                playlistSize = playlist.size,
                playlistIndex = startIndex,
                hasNext = playlist.size > 1,
                hasPrevious = playlist.size > 1,
            )
        }
    }

    // ---------------------------------------------------------------- 控制器

    private fun connect() {
        if (playlist.isEmpty()) {
            _state.update { it.copy(isLoading = false, errorMessage = "没有找到可播放的视频") }
            return
        }
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(playerListener)
                        val targetId = playlist.getOrNull(startIndex)?.id?.toString()
                        val alreadyPlaying = mediaController.mediaItemCount > 0 &&
                            mediaController.currentMediaItem?.mediaId == targetId
                        if (!alreadyPlaying) {
                            mediaController.setMediaItems(
                                playlist.map { it.toMediaItem() },
                                startIndex,
                                startPosition,
                            )
                            mediaController.setPlaybackSpeed(settings.defaultSpeed)
                            mediaController.prepare()
                        }
                        mediaController.playWhenReady = true
                        syncAll(mediaController)
                        _state.update { it.copy(isLoading = false, controllerReady = true) }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "播放器初始化失败",
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private val playerListener = object : Player.Listener {

        override fun onEvents(player: Player, events: Player.Events) {
            syncAll(player)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateCurrentItemInfo()
            restorePositionOnSwitch(reason)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                saveCurrentPosition(force = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(errorMessage = describeError(error), isLoading = false) }
        }
    }

    private fun syncAll(player: Player) {
        val rawDuration = player.duration
        val duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration
        val videoSize = player.videoSize
        _state.update { current ->
            current.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                isEnded = player.playbackState == Player.STATE_ENDED,
                isLoading = false,
                position = player.currentPosition.coerceAtLeast(0L),
                bufferedPosition = player.bufferedPosition.coerceAtLeast(0L),
                duration = if (duration > 0L) duration else current.duration,
                speed = player.playbackParameters.speed,
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem(),
                playlistIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                playlistSize = player.mediaItemCount,
                videoWidth = if (videoSize.width > 0) videoSize.width else current.videoWidth,
                videoHeight = if (videoSize.height > 0) videoSize.height else current.videoHeight,
            )
        }
        updateTracks(player.currentTracks)
        updateCurrentItemInfo()
    }

    private fun updateCurrentItemInfo() {
        val mediaController = controller ?: return
        val item = playlist.getOrNull(mediaController.currentMediaItemIndex) ?: return
        _state.update {
            it.copy(
                title = item.title,
                folderName = item.folderName,
                duration = if (item.duration > 0L && it.duration <= 0L) item.duration else it.duration,
            )
        }
    }

    /** 手动切换上一集/下一集时，恢复该视频上次的播放进度 */
    private fun restorePositionOnSwitch(reason: Int) {
        val mediaController = controller ?: return
        if (firstTransition) {
            firstTransition = false
            return
        }
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        ) {
            return
        }
        if (!settings.rememberPosition) return
        val index = mediaController.currentMediaItemIndex
        val item = playlist.getOrNull(index) ?: return
        if (item.id <= 0L) return
        val resume = savedPositions[item.id] ?: 0L
        val total = item.duration.takeIf { it > 0L } ?: 0L
        val valid = resume > RESUME_MIN_MS && (total <= 0L || resume < total - tailThreshold(total))
        if (valid) {
            mediaController.seekTo(index, resume)
        }
    }

    // ---------------------------------------------------------------- 轨道

    private fun updateTracks(tracks: Tracks) {
        val subtitleOptions = mutableListOf(TrackOption(TRACK_OFF, "关闭字幕", false))
        val audioOptions = mutableListOf<TrackOption>()
        val lookup = mutableMapOf<String, Pair<TrackGroup, Int>>()
        var subtitleSelectedId: String? = TRACK_OFF
        var audioSelectedId: String? = null

        for (group in tracks.groups) {
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                when (group.type) {
                    C.TRACK_TYPE_TEXT -> {
                        val id = "text-${group.mediaTrackGroup.id}-$index"
                        lookup[id] = group.mediaTrackGroup to index
                        subtitleOptions += TrackOption(id, describeFormat(format, "字幕", index), group.isTrackSelected(index))
                        if (group.isTrackSelected(index)) subtitleSelectedId = id
                    }

                    C.TRACK_TYPE_AUDIO -> {
                        val id = "audio-${group.mediaTrackGroup.id}-$index"
                        lookup[id] = group.mediaTrackGroup to index
                        audioOptions += TrackOption(id, describeFormat(format, "音轨", index), group.isTrackSelected(index))
                        if (group.isTrackSelected(index)) audioSelectedId = id
                    }
                }
            }
        }

        val subtitleDisabled = subtitleSelectedId == null
        val subtitles = if (subtitleDisabled) {
            subtitleOptions.mapIndexed { i, option -> if (i == 0) option.copy(selected = true) else option }
        } else {
            subtitleOptions
        }

        trackLookup = lookup
        _state.update {
            it.copy(
                subtitles = subtitles,
                audioTracks = audioOptions,
                selectedSubtitleId = subtitleSelectedId,
                selectedAudioId = audioSelectedId,
            )
        }
    }

    fun selectSubtitle(id: String) {
        val mediaController = controller ?: return
        val builder = mediaController.trackSelectionParameters.buildUpon()
        if (id == TRACK_OFF) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val target = trackLookup[id] ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            builder.setOverrideForType(TrackSelectionOverride(target.first, target.second))
        }
        mediaController.trackSelectionParameters = builder.build()
        _state.update { current ->
            current.copy(
                subtitles = current.subtitles.map { it.copy(selected = it.id == id) },
                selectedSubtitleId = if (id == TRACK_OFF) null else id,
            )
        }
    }

    fun selectAudioTrack(id: String) {
        val mediaController = controller ?: return
        val target = trackLookup[id] ?: return
        val builder = mediaController.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        builder.setOverrideForType(TrackSelectionOverride(target.first, target.second))
        mediaController.trackSelectionParameters = builder.build()
        _state.update { current ->
            current.copy(
                audioTracks = current.audioTracks.map { it.copy(selected = it.id == id) },
                selectedAudioId = id,
            )
        }
    }

    // ---------------------------------------------------------------- 播放控制

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.playbackState == Player.STATE_ENDED) {
            mediaController.seekTo(0L)
            mediaController.play()
        } else if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            mediaController.play()
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        val mediaController = controller ?: return
        mediaController.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(position = positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val mediaController = controller ?: return
        val target = (mediaController.currentPosition + deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun skipToNext() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val mediaController = controller ?: return
        if (mediaController.currentPosition > RESTART_THRESHOLD_MS || !mediaController.hasPreviousMediaItem()) {
            mediaController.seekTo(0L)
        } else {
            mediaController.seekToPreviousMediaItem()
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setResizeMode(mode: Int) {
        _state.update { it.copy(resizeMode = mode) }
    }

    fun retry() {
        val mediaController = controller ?: return
        _state.update { it.copy(errorMessage = null, isLoading = true) }
        mediaController.prepare()
        mediaController.playWhenReady = true
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // ---------------------------------------------------------------- 进度记录

    private fun startTicker() {
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val mediaController = controller
                if (mediaController != null) {
                    _state.update { current ->
                        val rawDuration = mediaController.duration
                        val duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration
                        current.copy(
                            position = mediaController.currentPosition.coerceAtLeast(0L),
                            bufferedPosition = mediaController.bufferedPosition.coerceAtLeast(0L),
                            duration = if (duration > 0L) duration else current.duration,
                            isPlaying = mediaController.isPlaying,
                        )
                    }
                    tick++
                    if (tick % SAVE_POSITION_TICKS == 0) saveCurrentPosition()
                }
                delay(500L)
            }
        }
    }

    private fun saveCurrentPosition(force: Boolean = false) {
        if (!settings.rememberPosition && !force) return
        val mediaController = controller ?: return
        val index = mediaController.currentMediaItemIndex
        val item = playlist.getOrNull(index) ?: return
        if (item.id <= 0L) return
        val position = mediaController.currentPosition
        if (position < RESUME_MIN_MS) return
        val rawDuration = mediaController.duration
        val duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration
        val nearEnd = duration > 0L && position >= duration - tailThreshold(duration)
        val value = if (nearEnd) 0L else position
        savedPositions = savedPositions.toMutableMap().apply { this[item.id] = value }
        // onCleared() 触发时 viewModelScope 已被取消，强制保存必须交给应用级作用域才能落盘
        val scope = if (force) container.appScope else viewModelScope
        scope.launch {
            runCatching { container.playbackPositionStore.save(item.id, value) }
        }
    }

    /**
     * 判定"已看完"的尾部区间：短视频用比例，长视频用固定 10 秒，
     * 否则像 15 秒的短片会整段都被当成结尾，进度永远记不住。
     */
    private fun tailThreshold(duration: Long): Long =
        minOf(RESUME_TAIL_MS, maxOf(RESUME_MIN_MS, (duration * RESUME_TAIL_RATIO).toLong()))

    // ---------------------------------------------------------------- 工具

    private fun describeFormat(format: Format, kind: String, index: Int): String {
        val parts = buildList {
            format.label?.takeIf { it.isNotBlank() }?.let { add(it) }
            languageLabel(format.language)?.let { add(it) }
            format.codecs?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.US)) }
            if (format.channelCount > 2) add("${format.channelCount} 声道")
        }
        return parts.joinToString(" · ").ifBlank { "$kind ${index + 1}" }
    }

    private fun languageLabel(language: String?): String? {
        if (language.isNullOrBlank() || language == "und") return null
        val name = runCatching { Locale.forLanguageTag(language).displayLanguage }.getOrNull()
        return name?.takeIf { it.isNotBlank() && it != language }
    }

    private fun describeError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "文件不存在或已被删除"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "网络连接失败，请检查网络"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "无法解析该视频文件"

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> "设备不支持该视频的编码格式"

        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "没有读取该文件的权限"
        else -> error.errorCodeName + (error.message?.let { "：$it" } ?: "")
    }

    override fun onCleared() {
        saveCurrentPosition(force = true)
        controller?.let { mediaController ->
            mediaController.removeListener(playerListener)
            if (!settings.backgroundPlay) {
                mediaController.pause()
            }
        }
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }

    companion object {
        private const val EXTERNAL_ID = -1L
        private const val TRACK_OFF = "off"
        private const val RESTART_THRESHOLD_MS = 3_000L
        private const val RESUME_MIN_MS = 3_000L
        private const val RESUME_TAIL_MS = 10_000L
        private const val RESUME_TAIL_RATIO = 0.05
        private const val SAVE_POSITION_TICKS = 10

        fun formatSpeedLabel(speed: Float): String =
            if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MediaPlayerApp
                PlayerViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}
