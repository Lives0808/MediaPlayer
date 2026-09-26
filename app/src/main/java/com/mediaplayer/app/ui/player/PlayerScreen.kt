@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.mediaplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mediaplayer.app.util.changeBrightness
import com.mediaplayer.app.util.changeVolume
import com.mediaplayer.app.util.currentBrightnessRatio
import com.mediaplayer.app.util.currentVolumeRatio
import com.mediaplayer.app.util.findActivity
import com.mediaplayer.app.util.formatDuration
import com.mediaplayer.app.util.formatSignedDuration
import com.mediaplayer.app.util.resetBrightness
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureMode { None, Seek, Volume, Brightness }

private data class HudState(
    val icon: ImageVector,
    val text: String,
    val stamp: Long,
)

private sealed interface ActiveSheet {
    data object None : ActiveSheet
    data object Speed : ActiveSheet
    data object Subtitle : ActiveSheet
    data object Audio : ActiveSheet
    data object Resize : ActiveSheet
}

private val RESIZE_OPTIONS = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "适应屏幕",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "拉伸铺满",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "裁剪铺满",
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "铺满宽度",
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "铺满高度",
)

private fun resizeModeLabel(mode: Int): String =
    RESIZE_OPTIONS.firstOrNull { it.first == mode }?.second ?: "适应屏幕"

private fun videoAspectRatio(width: Int, height: Int): Rational =
    if (width > 0 && height > 0) Rational(width, height) else Rational(16, 9)

/** 画中画动画的起始区域提示 */
private fun sourceRectHint(activity: Activity?): android.graphics.Rect? {
    val decorView = activity?.window?.decorView ?: return null
    val rect = android.graphics.Rect()
    if (!decorView.getGlobalVisibleRect(rect)) return null
    if (rect.width() <= 0 || rect.height() <= 0) return null
    return rect
}

private const val SEEK_STEP_MS = 10_000L
private const val CONTROL_HIDE_DELAY_MS = 4_000L
private const val VOLUME_SENSITIVITY = 1.4f
private const val BRIGHTNESS_SENSITIVITY = 1.1f

@Suppress("WrongConstant")
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findActivity<Activity>() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ActiveSheet>(ActiveSheet.None) }
    var hud by remember { mutableStateOf<HudState?>(null) }
    var inPip by remember { mutableStateOf(false) }

    val latestState by rememberUpdatedState(state)

    // 进入播放页：全屏沉浸 + 屏幕常亮
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            activity?.let { resetBrightness(it) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 默认横屏播放（等设置读取完成后再决定，避免先横屏又变回来）
    LaunchedEffect(state.settingsReady, state.autoLandscape) {
        if (state.settingsReady && state.autoLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // 画中画状态（用轮询代替监听器，兼容所有 API 26+ 设备）
    LaunchedEffect(activity) {
        val target = activity ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@LaunchedEffect
        while (true) {
            inPip = target.isInPictureInPictureMode
            delay(300L)
        }
    }

    // Android 12+ 按 Home 键自动进入画中画
    LaunchedEffect(state.autoPip, state.isPlaying, state.videoWidth, state.videoHeight, inPip) {
        val target = activity ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || inPip) return@LaunchedEffect
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAutoEnterEnabled(state.autoPip && state.isPlaying)
                .setAspectRatio(videoAspectRatio(state.videoWidth, state.videoHeight))
            sourceRectHint(target)?.let { params.setSourceRectHint(it) }
            target.setPictureInPictureParams(params.build())
        }
    }

    // 控制栏自动隐藏
    LaunchedEffect(controlsVisible, state.isPlaying, dragging, locked) {
        if (controlsVisible && state.isPlaying && !dragging && !locked) {
            delay(CONTROL_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // 手势 HUD 自动消失
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900L)
            hud = null
        }
    }

    BackHandler {
        when {
            locked -> locked = false
            sheet != ActiveSheet.None -> sheet = ActiveSheet.None
            else -> onBack()
        }
    }

    fun toggleOrientation() {
        val target = activity ?: return
        target.requestedOrientation =
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
    }

    fun enterPip() {
        val target = activity ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(videoAspectRatio(state.videoWidth, state.videoHeight))
            sourceRectHint(target)?.let { params.setSourceRectHint(it) }
            target.enterPictureInPictureMode(params.build())
        }
    }

    val resizeLabel = resizeModeLabel(state.resizeMode)
    val subtitleLabel = state.subtitles.firstOrNull { it.selected && it.id != "off" }?.label
    val audioLabel = state.audioTracks.firstOrNull { it.selected }?.label

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                if (state.controllerReady && view.player !== viewModel.player) {
                    view.player = viewModel.player
                }
                view.resizeMode = state.resizeMode
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // ---------- 手势层 ----------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val width = size.width.toFloat()
                            when {
                                offset.x < width * 0.35f -> {
                                    viewModel.seekBy(-SEEK_STEP_MS)
                                    hud = HudState(
                                        Icons.Filled.Replay10,
                                        formatSignedDuration(-SEEK_STEP_MS),
                                        System.nanoTime(),
                                    )
                                }

                                offset.x > width * 0.65f -> {
                                    viewModel.seekBy(SEEK_STEP_MS)
                                    hud = HudState(
                                        Icons.Filled.Forward10,
                                        formatSignedDuration(SEEK_STEP_MS),
                                        System.nanoTime(),
                                    )
                                }

                                else -> viewModel.togglePlayPause()
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var mode = GestureMode.None
                    var startPosition = 0L
                    var seekTarget = 0L
                    var accumulatedX = 0f
                    var dragStartX = 0f
                    var startVolumeRatio = 0f
                    var startBrightnessRatio = 0f
                    var totalVolumeDelta = 0f
                    var totalBrightnessDelta = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = GestureMode.None
                            accumulatedX = 0f
                            totalVolumeDelta = 0f
                            totalBrightnessDelta = 0f
                            dragStartX = offset.x
                            dragging = true
                        },
                        onDragEnd = {
                            if (mode == GestureMode.Seek) {
                                viewModel.seekTo(seekTarget)
                            }
                            mode = GestureMode.None
                            dragging = false
                        },
                        onDragCancel = {
                            mode = GestureMode.None
                            dragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                            val screenWidth = size.width.toFloat().coerceAtLeast(1f)

                            if (mode == GestureMode.None) {
                                mode = when {
                                    abs(dragAmount.x) > abs(dragAmount.y) -> GestureMode.Seek
                                    dragStartX < size.width / 2f -> GestureMode.Brightness
                                    else -> GestureMode.Volume
                                }
                                when (mode) {
                                    GestureMode.Seek -> {
                                        startPosition = latestState.position
                                        seekTarget = startPosition
                                    }

                                    GestureMode.Brightness -> {
                                        startBrightnessRatio = activity?.let { currentBrightnessRatio(it) } ?: 0f
                                    }

                                    GestureMode.Volume -> {
                                        startVolumeRatio = currentVolumeRatio(appContext)
                                    }

                                    GestureMode.None -> Unit
                                }
                            }

                            when (mode) {
                                GestureMode.Seek -> {
                                    accumulatedX += dragAmount.x
                                    val duration = latestState.duration
                                    if (duration > 0L) {
                                        val delta = (accumulatedX / screenWidth * duration).toLong()
                                        seekTarget = (startPosition + delta).coerceIn(0L, duration)
                                        hud = HudState(
                                            Icons.Filled.Forward10,
                                            "${formatDuration(seekTarget)} / ${formatDuration(duration)}",
                                            System.nanoTime(),
                                        )
                                    }
                                }

                                GestureMode.Brightness -> {
                                    totalBrightnessDelta += dragAmount.y / screenHeight
                                    val target = (startBrightnessRatio - totalBrightnessDelta * BRIGHTNESS_SENSITIVITY)
                                        .coerceIn(0.01f, 1f)
                                    activity?.let { changeBrightness(it, target - currentBrightnessRatio(it)) }
                                    hud = HudState(
                                        Icons.Filled.BrightnessHigh,
                                        "${(target * 100).roundToInt()}%",
                                        System.nanoTime(),
                                    )
                                }

                                GestureMode.Volume -> {
                                    totalVolumeDelta += dragAmount.y / screenHeight
                                    val target = (startVolumeRatio - totalVolumeDelta * VOLUME_SENSITIVITY)
                                        .coerceIn(0f, 1f)
                                    changeVolume(appContext, target - currentVolumeRatio(appContext))
                                    hud = HudState(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        "${(target * 100).roundToInt()}%",
                                        System.nanoTime(),
                                    )
                                }

                                GestureMode.None -> Unit
                            }
                        },
                    )
                },
        )

        if (state.isLoading && !state.controllerReady) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // ---------- 手势提示 ----------
        hud?.let { current ->
            GestureHud(
                icon = current.icon,
                text = current.text,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---------- 控制层 ----------
        AnimatedVisibility(
            visible = controlsVisible && !locked && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                PlayerTopBar(
                    title = state.title,
                    subtitle = buildString {
                        append(state.folderName)
                        if (state.videoWidth > 0 && state.videoHeight > 0) {
                            append(" · ${state.videoWidth}×${state.videoHeight}")
                        }
                        if (state.playlistSize > 1) {
                            append(" · ${state.playlistIndex + 1}/${state.playlistSize}")
                        }
                    },
                    onBack = onBack,
                    onEnterPip = { enterPip() },
                    onToggleOrientation = { toggleOrientation() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout),
                )
                PlayerCenterControls(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    isEnded = state.isEnded,
                    hasNext = state.hasNext,
                    hasPrevious = state.hasPrevious,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::skipToNext,
                    onPrevious = viewModel::skipToPrevious,
                    onSeekBack = { viewModel.seekBy(-SEEK_STEP_MS) },
                    onSeekForward = { viewModel.seekBy(SEEK_STEP_MS) },
                    modifier = Modifier.align(Alignment.Center),
                )
                PlayerBottomBar(
                    position = state.position,
                    duration = state.duration,
                    speed = state.speed,
                    resizeLabel = resizeLabel,
                    subtitleLabel = subtitleLabel,
                    audioLabel = audioLabel,
                    onSeek = viewModel::seekTo,
                    onDraggingChanged = { dragging = it },
                    onOpenSpeed = { sheet = ActiveSheet.Speed },
                    onOpenSubtitle = { sheet = ActiveSheet.Subtitle },
                    onOpenResize = { sheet = ActiveSheet.Resize },
                    onOpenAudio = { sheet = ActiveSheet.Audio },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout),
                )
            }
        }

        // ---------- 锁屏按钮 ----------
        if (locked || controlsVisible) {
            PlayerLockButton(
                locked = locked,
                onToggle = { locked = !locked },
                modifier = Modifier
                    .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }

    state.errorMessage?.let { message ->
        PlayerErrorDialog(
            message = message,
            onRetry = {
                viewModel.dismissError()
                viewModel.retry()
            },
            onDismiss = viewModel::dismissError,
        )
    }

    when (sheet) {
        ActiveSheet.Speed -> PlayerOptionSheet(
            title = "播放速度",
            options = viewModel.speeds.map {
                SheetOption(
                    id = it.value.toString(),
                    label = it.label,
                    selected = abs(it.value - state.speed) < 0.01f,
                )
            },
            onSelect = { id ->
                id.toFloatOrNull()?.let(viewModel::setSpeed)
                sheet = ActiveSheet.None
            },
            onDismiss = { sheet = ActiveSheet.None },
        )

        ActiveSheet.Subtitle -> PlayerOptionSheet(
            title = "字幕",
            options = state.subtitles.map {
                SheetOption(id = it.id, label = it.label, selected = it.selected)
            },
            onSelect = { id ->
                viewModel.selectSubtitle(id)
                sheet = ActiveSheet.None
            },
            onDismiss = { sheet = ActiveSheet.None },
        )

        ActiveSheet.Audio -> PlayerOptionSheet(
            title = "音轨",
            options = state.audioTracks.map {
                SheetOption(id = it.id, label = it.label, selected = it.selected)
            },
            onSelect = { id ->
                viewModel.selectAudioTrack(id)
                sheet = ActiveSheet.None
            },
            onDismiss = { sheet = ActiveSheet.None },
        )

        ActiveSheet.Resize -> PlayerOptionSheet(
            title = "画面比例",
            options = RESIZE_OPTIONS.map { option ->
                SheetOption(
                    id = option.first.toString(),
                    label = option.second,
                    selected = state.resizeMode == option.first,
                )
            },
            onSelect = { id ->
                id.toIntOrNull()?.let(viewModel::setResizeMode)
                sheet = ActiveSheet.None
            },
            onDismiss = { sheet = ActiveSheet.None },
        )

        ActiveSheet.None -> Unit
    }
}
