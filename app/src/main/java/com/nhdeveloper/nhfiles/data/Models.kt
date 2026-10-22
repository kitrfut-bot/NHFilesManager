package com.nhdeveloper.nhfiles.data

import java.io.File

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, ARCHIVE, APK, TEXT, PDF, DOCUMENT, OTHER }

enum class SortBy(val label: String) {
    NAME("Name"), SIZE("Size"), DATE("Date"), TYPE("Type")
}

enum class Conflict { REPLACE, SKIP, RENAME }

data class FileItem(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int,
    val kind: FileKind,
) {
    val path: String get() = file.absolutePath
}

data class AppSettings(
    val theme: Int = 0, // 0 = system, 1 = light, 2 = dark
    val startDir: String = "",
    val showExtensions: Boolean = true,
    val showHidden: Boolean = false,
    val sortBy: SortBy = SortBy.NAME,
    val sortAsc: Boolean = true,
    val grid: Boolean = false,
    val confirmDelete: Boolean = true,
    val keepRecents: Boolean = true,
    val bookmarks: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
)

data class OpProgress(
    val title: String,
    val done: Long,
    val total: Long,
    val current: String = "",
    val bytes: Boolean = true,
)

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val fraction: Float get() = if (total <= 0) 0f else (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

data class ScanResult(
    val byCategory: Map<FileKind, Long>,
    val fileCount: Int,
    val largest: List<FileItem>,
    val duplicates: List<List<FileItem>>,
)
