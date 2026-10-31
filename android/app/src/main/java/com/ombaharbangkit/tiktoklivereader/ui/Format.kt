package com.ombaharbangkit.tiktoklivereader.ui

/** Format lama menonton — sama dengan fmt_dur() di main.py. */
fun fmtDur(sec: Long): String {
    val s = sec.toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return when {
        h > 0 -> "$h jam $m mnt"
        m > 0 -> "$m mnt $x dtk"
        else -> "$x dtk"
    }
}

/** Format kecepatan jaringan — sama dengan fmt_speed() di main.py. */
fun fmtSpeed(bytesPerSec: Double, unit: String): String {
    val b = bytesPerSec.coerceAtLeast(0.0)
    val (value, label) = when (unit) {
        "mbps" -> (b * 8 / (1024 * 1024)) to "Mbps"
        "MBps" -> (b / (1024 * 1024)) to "MBps"
        else -> (b * 8 / 1024) to "Kbps"
    }
    return if (value < 10) String.format(java.util.Locale.US, "%.1f %s", value, label)
    else String.format(java.util.Locale.US, "%.0f %s", value, label)
}
