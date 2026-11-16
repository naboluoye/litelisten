package com.litelisten.player.core.common

/**
 * 扫描时的时长筛选（纯函数，可直接单测）。
 *
 * - 时长为 0 / 未知：始终保留——有些 ROM 或网络流拿不到时长，
 *   凭 0 就丢会把正常歌曲误删；用户设置的阈值只作用于「已知时长」。
 * - [minMs] > 0 时，丢弃 0 < duration < minMs 的短音频（几十秒的提示音、语音片段）。
 * - [maxMs] > 0 时，丢弃 duration > maxMs 的长录音（整场会议、播客合集）。
 * - min / max 为 0 表示不限制这一侧。
 */
object DurationFilter {
    fun keep(durationMs: Long, minMs: Long, maxMs: Long): Boolean {
        if (durationMs <= 0L) return true
        if (minMs > 0L && durationMs < minMs) return false
        if (maxMs > 0L && durationMs > maxMs) return false
        return true
    }

    /** 秒 → 毫秒，供设置值（秒）转成筛选阈值；<=0 视为不限制。 */
    fun secondsToMs(seconds: Int): Long = if (seconds > 0) seconds * 1000L else 0L
}
