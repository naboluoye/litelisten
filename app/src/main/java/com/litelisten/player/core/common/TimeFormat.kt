package com.litelisten.player.core.common

import java.util.Locale

object TimeFormat {
    /** 小于 1 小时 -> mm:ss；否则 h:mm:ss。负数按 0 处理。 */
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
