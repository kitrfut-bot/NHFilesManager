package com.nhdeveloper.nhfiles.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.PriorityQueue

class StorageScanner(private val repo: FileRepository) {

    /** Walks [root] once: category totals, the 100 largest files and duplicate groups (SHA-256). */
    suspend fun scan(root: File, onStatus: (String) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val cats = HashMap<FileKind, Long>()
        val heap = PriorityQueue<FileItem>(compareBy<FileItem> { it.size })
        val bySize = HashMap<Long, ArrayList<File>>()
        var files = 0

        repo.walk(root, true) { f ->
            if (f.isFile) {
                files++
                val size = f.length()
                val kind = FileUtils.kindOf(f.name, false)
                cats[kind] = (cats[kind] ?: 0L) + size
                if (heap.size < 100 || size > (heap.peek()?.size ?: 0L)) {
                    heap.add(repo.toItem(f, false))
                    if (heap.size > 100) heap.poll()
                }
                if (size >= MIN_DUP_SIZE) bySize.getOrPut(size) { ArrayList() }.add(f)
                if (files % 500 == 0) onStatus("Scanning… $files files")
            }
        }

        val candidates = bySize.values.filter { it.size > 1 }
        val groups = ArrayList<List<FileItem>>()
        var done = 0
        for (list in candidates) {
            currentCoroutineContext().ensureActive()
            val byHash = HashMap<String, ArrayList<File>>()
            for (f in list) {
                currentCoroutineContext().ensureActive()
                val h = hash(f) ?: continue
                byHash.getOrPut(h) { ArrayList() }.add(f)
            }
            for (g in byHash.values) {
                if (g.size > 1) groups.add(g.map { repo.toItem(it, false) })
            }
            done++
            onStatus("Comparing files… $done/${candidates.size}")
        }
        groups.sortByDescending { g -> g[0].size * (g.size - 1) }

        ScanResult(
            byCategory = cats,
            fileCount = files,
            largest = heap.toList().sortedByDescending { it.size },
            duplicates = groups.take(200),
        )
    }

    private suspend fun hash(f: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { input ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: java.io.IOException) {
            null
        }
    }

    companion object {
        const val MIN_DUP_SIZE = 32L * 1024
    }
}
