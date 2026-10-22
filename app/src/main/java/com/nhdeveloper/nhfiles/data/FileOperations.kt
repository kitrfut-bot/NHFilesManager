package com.nhdeveloper.nhfiles.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Throttled progress reporter shared by long-running operations. */
class Tracker(
    private val title: String,
    private val total: Long,
    private val bytes: Boolean,
    private val onProgress: (OpProgress) -> Unit,
) {
    private var done = 0L
    private var last = 0L

    fun add(n: Long, current: String) {
        done += n
        val now = System.nanoTime()
        if (now - last > 100_000_000L || done >= total) {
            last = now
            onProgress(OpProgress(title, done, total, current, bytes))
        }
    }
}

class FileOperations {

    fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\u0000')) {
            throw IOException("Invalid name")
        }
    }

    suspend fun sizeOf(f: File): Long {
        currentCoroutineContext().ensureActive()
        if (FileUtils.isSymlink(f)) return 0L
        if (!f.isDirectory) return f.length()
        var s = 0L
        f.listFiles()?.forEach { s += sizeOf(it) }
        return s
    }

    fun createFolder(parent: File, name: String): File {
        validateName(name)
        val f = File(parent, name)
        if (FileUtils.present(f)) throw IOException("\"$name\" already exists")
        if (!f.mkdir()) throw IOException("Could not create folder \"$name\"")
        return f
    }

    fun createFile(parent: File, name: String): File {
        validateName(name)
        val f = File(parent, name)
        if (FileUtils.present(f)) throw IOException("\"$name\" already exists")
        if (!f.createNewFile()) throw IOException("Could not create file \"$name\"")
        return f
    }

    fun rename(f: File, newName: String): File {
        validateName(newName)
        val parent = f.parentFile ?: throw IOException("Cannot rename this item")
        val target = File(parent, newName)
        if (target.name == f.name) return f
        if (FileUtils.present(target) && !FileUtils.sameFile(f, target)) {
            throw IOException("\"$newName\" already exists")
        }
        if (!f.renameTo(target)) throw IOException("Rename failed")
        return target
    }

    suspend fun delete(files: List<File>, onProgress: (OpProgress) -> Unit): Int = withContext(Dispatchers.IO) {
        val tracker = Tracker("Deleting", files.size.toLong(), false, onProgress)
        var n = 0
        for (f in files) {
            currentCoroutineContext().ensureActive()
            deleteTree(f)
            n++
            tracker.add(1, f.name)
        }
        n
    }

    private suspend fun deleteTree(f: File) {
        currentCoroutineContext().ensureActive()
        if (!FileUtils.isSymlink(f) && f.isDirectory) {
            f.listFiles()?.forEach { deleteTree(it) }
        }
        if (!f.delete() && FileUtils.present(f)) throw IOException("Cannot delete \"${f.name}\"")
    }

    /**
     * Copies or moves [sources] into [destDir]. Nothing is overwritten unless [conflict] is REPLACE,
     * and replaced files are written to a temporary file first. Returns the number of top-level items handled.
     */
    suspend fun transfer(
        sources: List<File>,
        destDir: File,
        move: Boolean,
        conflict: Conflict,
        onProgress: (OpProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        if (!destDir.isDirectory) throw IOException("Destination is not a folder")
        val destCanon = destDir.canonicalFile
        val sizes = sources.map { sizeOf(it) }
        val total = sizes.sum()
        if (!move && total > destDir.usableSpace) {
            throw IOException("Not enough free space (need ${FileUtils.formatSize(total)})")
        }
        val tracker = Tracker(if (move) "Moving" else "Copying", total, true, onProgress)
        val copiedFiles = ArrayList<File>()
        val visitedDirs = ArrayList<File>()
        var count = 0

        for ((index, src) in sources.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (!FileUtils.present(src)) continue
            val srcCanon = src.canonicalFile
            if (src.isDirectory && (destCanon == srcCanon || destCanon.path.startsWith(srcCanon.path + File.separator))) {
                throw IOException("Cannot copy \"${src.name}\" into itself")
            }
            val target = resolve(src, File(destDir, src.name), conflict, move)
            if (target == null) {
                tracker.add(sizes[index], src.name)
                continue
            }
            if (move && !FileUtils.present(target) && src.renameTo(target)) {
                tracker.add(sizes[index], src.name)
                count++
                continue
            }
            copyEntry(src, target, conflict, move, tracker, copiedFiles, visitedDirs)
            count++
        }
        if (move) {
            copiedFiles.forEach { it.delete() }
            visitedDirs.asReversed().forEach { it.delete() } // succeeds only for empty folders
        }
        count
    }

    /** Returns the file to write to, or null when the item must be skipped. */
    private fun resolve(src: File, desired: File, conflict: Conflict, move: Boolean): File? {
        if (!FileUtils.present(desired)) return desired
        if (FileUtils.sameFile(src, desired)) {
            return if (move) null else FileUtils.uniqueFile(desired.parentFile!!, desired.name)
        }
        if (src.isDirectory && desired.isDirectory && !FileUtils.isSymlink(desired)) {
            return if (conflict == Conflict.RENAME) FileUtils.uniqueFile(desired.parentFile!!, desired.name) else desired
        }
        return when (conflict) {
            Conflict.SKIP -> null
            Conflict.RENAME -> FileUtils.uniqueFile(desired.parentFile!!, desired.name)
            Conflict.REPLACE -> desired
        }
    }

    private suspend fun copyEntry(
        src: File,
        dst: File,
        conflict: Conflict,
        move: Boolean,
        tracker: Tracker,
        copiedFiles: MutableList<File>,
        visitedDirs: MutableList<File>,
    ) {
        currentCoroutineContext().ensureActive()
        if (FileUtils.isSymlink(src)) return // symbolic links are never followed or copied
        if (src.isDirectory) {
            if (FileUtils.present(dst) && !dst.isDirectory) throw IOException("\"${dst.name}\" exists and is not a folder")
            if (!dst.exists() && !dst.mkdirs()) throw IOException("Cannot create folder \"${dst.name}\"")
            visitedDirs.add(src)
            val kids = src.listFiles() ?: throw IOException("Cannot read \"${src.name}\"")
            for (k in kids) {
                val t = resolve(k, File(dst, k.name), conflict, move)
                if (t == null) {
                    if (!k.isDirectory) tracker.add(k.length(), k.name)
                    continue
                }
                copyEntry(k, t, conflict, move, tracker, copiedFiles, visitedDirs)
            }
        } else {
            copyFile(src, dst, tracker)
            copiedFiles.add(src)
        }
    }

    private suspend fun copyFile(src: File, dst: File, tracker: Tracker) {
        val replacing = dst.exists()
        val tmp = if (replacing) File(dst.parentFile, "." + dst.name + ".nhtmp") else dst
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        tracker.add(n.toLong(), src.name)
                    }
                }
            }
            if (replacing && !tmp.renameTo(dst)) {
                throw IOException("Cannot replace \"${dst.name}\"")
            }
            dst.setLastModified(src.lastModified())
        } catch (t: Throwable) {
            if (tmp.exists()) tmp.delete()
            throw t
        }
    }
}
