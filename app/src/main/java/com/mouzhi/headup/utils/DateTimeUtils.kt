package com.mouzhi.headup.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    fun formatDateTime(): Pair<String, String> {
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MM/dd EEEE", Locale.getDefault())
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        return Pair(timeStr, dateStr)
    }
}
