package com.nhdeveloper.nhfiles.vpo.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File

object MediaRepository {

    fun load(ctx: Context, trees: Set<String>, allowStore: Boolean): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        if (allowStore) {
            try { out += queryStore(ctx) } catch (_: Exception) { }
        }
        val known = out.map { "${it.name}|${it.size}" }.toHashSet()
        for (t in trees) {
            try {
                for (v in scanTree(ctx, Uri.parse(t))) {
                    if (known.add("${v.name}|${v.size}")) out += v
                }
            } catch (_: Exception) { }
        }
        return out
    }

    private fun queryStore(ctx: Context): List<VideoItem> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )
        projection += if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.RELATIVE_PATH
        else MediaStore.Video.Media.DATA
        val list = ArrayList<VideoItem>()
        ctx.contentResolver.query(
            collection, projection.toTypedArray(), null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iBid = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val iBname = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val iPath = c.getColumnIndexOrThrow(projection.last())
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val size = c.getLong(iSize)
                if (size <= 0) continue
                val raw = c.getString(iPath) ?: ""
                var sub: String? = null
                val path: String
                if (Build.VERSION.SDK_INT >= 29) {
                    path = raw
                } else {
                    path = File(raw).parent ?: ""
                    val base = raw.substringBeforeLast('.')
                    for (ext in listOf("srt", "vtt")) {
                        val f = File("$base.$ext")
                        if (f.exists()) { sub = Uri.fromFile(f).toString(); break }
                    }
                }
                list += VideoItem(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = c.getString(iName) ?: "video_$id",
                    durationMs = c.getLong(iDur),
                    width = c.getInt(iW), height = c.getInt(iH), size = size,
                    folderKey = "bucket:" + c.getLong(iBid),
                    folderName = c.getString(iBname) ?: "Internal Storage",
                    folderPath = path,
                    dateAdded = c.getLong(iDate),
                    subtitleUri = sub
                )
            }
        }
        return list
    }

    private fun scanTree(ctx: Context, tree: Uri): List<VideoItem> {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyList()
        val out = ArrayList<VideoItem>()
        walk(ctx, root, out, 0)
        return out
    }

    private fun walk(ctx: Context, dir: DocumentFile, out: MutableList<VideoItem>, depth: Int) {
        if (depth > 8) return
        val files = dir.listFiles()
        val subs = HashMap<String, Uri>()
        for (f in files) {
            val n = f.name ?: continue
            val ext = n.substringAfterLast('.', "").lowercase()
            if (!f.isDirectory && (ext == "srt" || ext == "vtt")) subs[n.substringBeforeLast('.')] = f.uri
        }
        for (f in files) {
            if (f.isDirectory) { walk(ctx, f, out, depth + 1); continue }
            if (f.type?.startsWith("video/") != true) continue
            val name = f.name ?: continue
            var dur = 0L; var w = 0; var h = 0
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, f.uri)
                dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            } catch (_: Exception) {
            } finally {
                try { r.release() } catch (_: Exception) { }
            }
            out += VideoItem(
                uri = f.uri.toString(), name = name, durationMs = dur, width = w, height = h,
                size = f.length(), folderKey = "saf:" + dir.uri, folderName = dir.name ?: "Folder",
                folderPath = dir.name ?: "", dateAdded = f.lastModified() / 1000,
                subtitleUri = subs[name.substringBeforeLast('.')]?.toString()
            )
        }
    }
}
