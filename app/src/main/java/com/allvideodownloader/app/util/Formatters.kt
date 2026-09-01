package com.allvideodownloader.app.util

import android.text.format.DateUtils
import java.util.concurrent.TimeUnit

object Formatters {

    fun bytes(value: Long): String {
        if (value <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "${value} ${units[0]}" else String.format("%.1f %s", size, units[unit])
    }

    fun duration(millis: Long): String? {
        if (millis <= 0) return null
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun relativeDate(epochSeconds: Long): String? {
        if (epochSeconds <= 0) return null
        return DateUtils.getRelativeTimeSpanString(
            TimeUnit.SECONDS.toMillis(epochSeconds),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    fun progressLabel(downloaded: Long, total: Long): String =
        if (total > 0) "${bytes(downloaded)} of ${bytes(total)}" else bytes(downloaded)
}
