package com.nhdeveloper.nhfiles.data

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

class FileRepository {

    val root: File get() = Environment.getExternalStorageDirectory()

    fun list(dir: File, showHidden: Boolean): List<FileItem> {
        if (!dir.isDirectory) throw IOException("This folder does not exist or is not a folder")
        val files = dir.listFiles() ?: throw IOException("Cannot read this folder (access denied)")
        val out = ArrayList<FileItem>(files.size)
        for (f in files) {
            if (!showHidden && f.name.startsWith(".")) continue
            out.add(toItem(f, true))
        }
        return out
    }

    fun toItem(f: File, countChildren: Boolean): FileItem {
        val dir = f.isDirectory
        return FileItem(
            file = f,
            name = f.name.ifEmpty { f.path },
            isDir = dir,
            size = if (dir) 0L else f.length(),
            modified = f.lastModified(),
            childCount = if (dir && countChildren) (f.list()?.size ?: 0) else 0,
            kind = FileUtils.kindOf(f.name, dir),
        )
    }

    fun sorted(items: List<FileItem>, by: SortBy, asc: Boolean): List<FileItem> {
        val base: Comparator<FileItem> = when (by) {
            SortBy.NAME -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.SIZE -> compareBy<FileItem> { it.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.DATE -> compareBy<FileItem> { it.modified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.TYPE -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { FileUtils.extension(it.name) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val dir: Comparator<FileItem> = if (asc) base else base.reversed()
        val foldersFirst: Comparator<FileItem> = compareBy<FileItem> { !it.isDir }
        return items.sortedWith(foldersFirst.then(dir))
    }

    fun storage(): StorageInfo = try {
        val s = StatFs(root.path)
        StorageInfo(s.totalBytes, s.availableBytes)
    } catch (e: Exception) {
        StorageInfo(0, 0)
    }

    /** Depth-first walk that never follows symbolic links and can be cancelled. */
    suspend fun walk(start: File, showHidden: Boolean, visit: suspend (File) -> Unit) {
        val stack = ArrayList<File>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeAt(stack.lastIndex)
            val children = dir.listFiles() ?: continue
            for (c in children) {
                if (!showHidden && c.name.startsWith(".")) continue
                if (FileUtils.isSymlink(c)) continue
                visit(c)
                if (c.isDirectory) stack.add(c)
            }
        }
    }
}
