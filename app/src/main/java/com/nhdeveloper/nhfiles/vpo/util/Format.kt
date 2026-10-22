package com.nhdeveloper.nhfiles.vpo.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nhdeveloper.nhfiles.vpo.data.VideoItem
import java.util.Locale

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0; val mb = kb / 1024; val gb = mb / 1024
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun VideoItem.metaLine(): String =
    listOf(formatDuration(durationMs), resolutionLabel, formatSize(size)).filter { it.isNotEmpty() }.joinToString(" • ")

fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO
    else android.Manifest.permission.READ_EXTERNAL_STORAGE

fun hasVideoPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, videoPermission()) == PackageManager.PERMISSION_GRANTED
