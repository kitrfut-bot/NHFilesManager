package com.nhdeveloper.nhfiles.vpo.data

import android.net.Uri
import kotlin.math.min

data class VideoItem(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val folderKey: String,
    val folderName: String,
    val folderPath: String,
    val dateAdded: Long,
    val subtitleUri: String? = null
) {
    val parsedUri: Uri get() = Uri.parse(uri)
    val title: String get() = name.substringBeforeLast('.', name)
    val resolutionLabel: String
        get() {
            val s = min(width, height)
            return when {
                s <= 0 -> ""
                s >= 2160 -> "4K"
                s >= 1440 -> "1440p"
                s >= 1080 -> "1080p"
                s >= 720 -> "720p"
                s >= 480 -> "480p"
                s >= 360 -> "360p"
                else -> "${s}p"
            }
        }
}

data class FolderItem(val key: String, val name: String, val path: String, val count: Int)

data class ContinueItem(val video: VideoItem, val positionMs: Long, val progress: Int)
