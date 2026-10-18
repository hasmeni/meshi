package com.meshi.code.ui.util

import java.text.SimpleDateFormat
import java.util.*

/** Formats a timestamp as "Today HH:mm", "Yesterday HH:mm", or "MMM d, HH:mm". */
fun formatMessageTime(timestampMs: Long): String {
    val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(msgCal, today)     -> "Today $hhmm"
        isSameDay(msgCal, yesterday) -> "Yesterday $hhmm"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}

private fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
