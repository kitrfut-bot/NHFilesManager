package com.nhdeveloper.nhfiles.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ZipEntryInfo(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val compressed: Long,
    val modified: Long,
)

/** ZIP support built on java.util.zip. Password-protected archives are not supported. */
class ZipManager(private val ops: FileOperations) {

    suspend fun list(zip: File): List<ZipEntryInfo> = withContext(Dispatchers.IO) {
        try {
            ZipFile(zip).use { z ->
                val out = ArrayList<ZipEntryInfo>()
                val en = z.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    out.add(ZipEntryInfo(e.name, e.isDirectory, e.size, e.compressedSize, e.time))
                }
                out
            }
        } catch (e: ZipException) {
            throw IOException("Corrupt or unsupported archive: ${e.message}")
        }
    }

    suspend fun extract(
        zip: File,
        destDir: File,
        only: Set<String>?,
        onProgress: (OpProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Cannot create folder \"${destDir.name}\"")
        val destCanon = destDir.canonicalFile
        var count = 0
        try {
            ZipFile(zip).use { z ->
                val selected = ArrayList<ZipEntry>()
                val en = z.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (only == null || only.any { s -> e.name == s || (s.endsWith("/") && e.name.startsWith(s)) }) {
                        selected.add(e)
                    }
                }
                val total = selected.sumOf { if (it.size > 0) it.size else 0L }
                if (total > destDir.usableSpace) {
                    throw IOException("Not enough free space (need ${FileUtils.formatSize(total)})")
                }
                val tracker = Tracker("Extracting", total, true, onProgress)
                for (e in selected) {
                    currentCoroutineContext().ensureActive()
                    val out = File(destDir, e.name)
                    val canon = out.canonicalFile
                    if (canon == destCanon) continue
                    // ZIP-slip protection: every entry must stay inside the destination folder.
                    if (!canon.path.startsWith(destCanon.path + File.separator)) {
                        throw IOException("Blocked unsafe entry path: ${e.name}")
                    }
                    if (e.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    val target = if (FileUtils.present(out)) FileUtils.uniqueFile(out.parentFile!!, out.name) else out
                    try {
                        z.getInputStream(e).use { input ->
                            FileOutputStream(target).use { os ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    os.write(buf, 0, n)
                                    tracker.add(n.toLong(), e.name)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        target.delete()
                        throw t
                    }
                    if (e.time > 0) target.setLastModified(e.time)
                    count++
                }
            }
        } catch (e: ZipException) {
            throw IOException("Cannot extract: ${e.message}")
        }
        count
    }

    suspend fun create(sources: List<File>, out: File, onProgress: (OpProgress) -> Unit) = withContext(Dispatchers.IO) {
        val total = sources.sumOf { ops.sizeOf(it) }
        val tracker = Tracker("Compressing", total, true, onProgress)
        val tmp = File(out.parentFile, "." + out.name + ".nhtmp")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
                val used = HashSet<String>()
                for (s in sources) addTree(zos, s, s.name, out, tracker, used)
            }
            if (!tmp.renameTo(out)) throw IOException("Cannot write \"${out.name}\"")
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private suspend fun addTree(
        zos: ZipOutputStream,
        f: File,
        entryName: String,
        skip: File,
        tracker: Tracker?,
        used: MutableSet<String>,
    ) {
        currentCoroutineContext().ensureActive()
        if (FileUtils.isSymlink(f) || FileUtils.sameFile(f, skip)) return
        if (f.isDirectory) {
            val dirName = "$entryName/"
            if (!used.add(dirName)) throw IOException("Entry already exists: $dirName")
            val ze = ZipEntry(dirName)
            ze.time = f.lastModified()
            zos.putNextEntry(ze)
            zos.closeEntry()
            f.listFiles()?.forEach { addTree(zos, it, "$entryName/${it.name}", skip, tracker, used) }
        } else {
            if (!used.add(entryName)) throw IOException("Entry already exists: $entryName")
            val ze = ZipEntry(entryName)
            ze.time = f.lastModified()
            zos.putNextEntry(ze)
            FileInputStream(f).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    zos.write(buf, 0, n)
                    tracker?.add(n.toLong(), f.name)
                }
            }
            zos.closeEntry()
        }
    }

    suspend fun deleteEntries(zip: File, names: Set<String>, onProgress: (OpProgress) -> Unit) =
        rewrite(zip, names, null, null, emptyList(), "", onProgress)

    suspend fun renameEntry(zip: File, from: String, to: String, onProgress: (OpProgress) -> Unit) =
        rewrite(zip, emptySet(), from, to, emptyList(), "", onProgress)

    suspend fun addFiles(zip: File, files: List<File>, prefix: String, onProgress: (OpProgress) -> Unit) =
        rewrite(zip, emptySet(), null, null, files, prefix, onProgress)

    /** Rewrites the archive into a temp file and swaps it in only after everything succeeded. */
    private suspend fun rewrite(
        zip: File,
        deletes: Set<String>,
        renameFrom: String?,
        renameTo: String?,
        adds: List<File>,
        addPrefix: String,
        onProgress: (OpProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val tmp = File(zip.parentFile, "." + zip.name + ".nhtmp")
        try {
            ZipFile(zip).use { zf ->
                val tracker = Tracker("Updating archive", zf.size().toLong() + adds.size, false, onProgress)
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
                    val used = HashSet<String>()
                    val en = zf.entries()
                    while (en.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        val e = en.nextElement()
                        if (deletes.any { d -> e.name == d || (d.endsWith("/") && e.name.startsWith(d)) }) continue
                        var newName = e.name
                        if (renameFrom != null && renameTo != null &&
                            (e.name == renameFrom || (renameFrom.endsWith("/") && e.name.startsWith(renameFrom)))
                        ) {
                            newName = renameTo + e.name.substring(renameFrom.length)
                        }
                        if (!used.add(newName)) throw IOException("Duplicate entry name: $newName")
                        val ne = ZipEntry(newName)
                        if (e.time > 0) ne.time = e.time
                        zos.putNextEntry(ne)
                        if (!e.isDirectory) {
                            zf.getInputStream(e).use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    zos.write(buf, 0, n)
                                }
                            }
                        }
                        zos.closeEntry()
                        tracker.add(1, e.name)
                    }
                    for (f in adds) {
                        addTree(zos, f, addPrefix + f.name, zip, null, used)
                        tracker.add(1, f.name)
                    }
                }
            }
            if (!tmp.renameTo(zip)) throw IOException("Cannot update \"${zip.name}\"; the original is unchanged")
        } catch (t: Throwable) {
            tmp.delete()
            if (t is ZipException) throw IOException("Archive error: ${t.message}")
            throw t
        }
    }

    /** Reads and decompresses every entry (verifying CRC of compressed entries). */
    suspend fun test(zip: File): String = withContext(Dispatchers.IO) {
        try {
            ZipFile(zip).use { z ->
                var n = 0
                val buf = ByteArray(64 * 1024)
                val en = z.entries()
                while (en.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val e = en.nextElement()
                    if (!e.isDirectory) {
                        z.getInputStream(e).use { input -> while (input.read(buf) >= 0) { /* verify */ } }
                    }
                    n++
                }
                "Archive OK: $n entries verified"
            }
        } catch (e: ZipException) {
            throw IOException("Archive test failed: ${e.message}")
        }
    }

    companion object {
        /** Immediate children of [prefix] ("" or "dir/") derived from the flat entry list. */
        fun children(all: List<ZipEntryInfo>, prefix: String): List<ZipEntryInfo> {
            val map = LinkedHashMap<String, ZipEntryInfo>()
            for (e in all) {
                if (!e.name.startsWith(prefix) || e.name == prefix) continue
                val rest = e.name.substring(prefix.length)
                val slash = rest.indexOf('/')
                val isDir = slash >= 0
                val key = if (isDir) rest.substring(0, slash + 1) else rest
                if (!map.containsKey(key)) {
                    map[key] = ZipEntryInfo(
                        prefix + key, isDir,
                        if (isDir) 0L else e.size,
                        if (isDir) 0L else e.compressed,
                        e.modified,
                    )
                }
            }
            return map.values.sortedWith(
                compareBy<ZipEntryInfo> { !it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }
    }
}
