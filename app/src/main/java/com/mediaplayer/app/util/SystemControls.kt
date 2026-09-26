package com.mediaplayer.app.util

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import kotlin.math.roundToInt

/** 音量 / 亮度手势控制 */

fun currentVolumeRatio(context: Context): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0f
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0f
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

/** delta 为 -1..1 的增量比例 */
fun changeVolume(context: Context, delta: Float): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0f
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0f
    val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val target = (current + delta * max).roundToInt().coerceIn(0, max)
    runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    return target.toFloat() / max
}

fun currentBrightnessRatio(activity: Activity): Float {
    val attribute = activity.window.attributes.screenBrightness
    if (attribute >= 0f) return attribute
    val system = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (system / 255f).coerceIn(0.01f, 1f)
}

fun changeBrightness(activity: Activity, delta: Float): Float {
    val current = currentBrightnessRatio(activity)
    val target = (current + delta).coerceIn(0.01f, 1f)
    val attributes: WindowManager.LayoutParams = activity.window.attributes
    attributes.screenBrightness = target
    activity.window.attributes = attributes
    return target
}

/** 退出播放页时把亮度交还给系统 */
fun resetBrightness(activity: Activity) {
    val attributes: WindowManager.LayoutParams = activity.window.attributes
    attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    activity.window.attributes = attributes
}
