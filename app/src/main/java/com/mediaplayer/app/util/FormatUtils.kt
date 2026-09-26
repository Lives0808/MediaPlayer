package com.mediaplayer.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 把毫秒格式化成 00:00 / 0:00:00 */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 带符号的时长，用于快进/快退提示 */
fun formatSignedDuration(ms: Long): String {
    val sign = if (ms < 0) "-" else "+"
    return sign + formatDuration(abs(ms))
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${bytes} B"
    } else {
        String.format(Locale.US, "%.2f %s", value, units[index])
    }
}

// SimpleDateFormat 的构造开销不小（解析 pattern、初始化 Calendar），而 formatDate 会在视频列表里逐项调用；
// 缓存实例复用，SimpleDateFormat 不是线程安全的，格式化时加锁。
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** 把秒级时间戳转成 yyyy-MM-dd */
fun formatDate(seconds: Long): String {
    if (seconds <= 0L) return "未知"
    return synchronized(dateFormat) { dateFormat.format(Date(seconds * 1000)) }
}

/** 倍速显示文案 */
fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

/** 把播放进度百分比转成 0..1 */
fun progressOf(position: Long, duration: Long): Float {
    if (duration <= 0L) return 0f
    return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}
