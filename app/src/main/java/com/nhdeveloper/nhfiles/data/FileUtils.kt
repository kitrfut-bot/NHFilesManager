package com.nhdeveloper.nhfiles.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val video = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "mpg", "mpeg", "flv")
    private val audio = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr", "wma", "mid")
    private val archive = setOf("zip", "jar", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val text = setOf(
        "txt", "html", "htm", "xhtml", "css", "scss", "sass", "less", "js", "mjs", "cjs", "jsx", "ts", "tsx",
        "vue", "json", "json5", "jsonc", "xml", "xsd", "xsl", "md", "markdown", "ini", "cfg", "conf", "config",
        "properties", "env", "log", "csv", "tsv", "kt", "kts", "java", "py", "sh", "bash", "zsh", "yml", "yaml",
        "gradle", "toml", "sql", "c", "h", "hpp", "hh", "cc", "cpp", "cxx", "cs", "php", "svg", "smali", "bat",
        "cmd", "ps1", "psm1", "rtf.txt", "gitignore", "gitattributes", "dockerignore", "editorconfig", "rb",
        "go", "rs", "swift", "m", "mm", "lua", "pl", "pm", "r", "dart", "groovy", "scala", "clj", "asm", "s",
        "pro", "proguard", "cmake", "mk", "ejs", "hbs", "twig", "txtpb", "service", "desktop", "lock", "map",
        "diff", "patch", "nhhtml",
    )
    private val doc = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub")

    /** Extensionless filenames (Makefile, Dockerfile, LICENSE...) that are still plain text. */
    private val textFileNames = setOf(
        "makefile", "dockerfile", "jenkinsfile", "rakefile", "gemfile", "procfile", "vagrantfile",
        "readme", "license", "licence", "notice", "changelog", "authors", "contributing", "gitignore",
        "gitattributes", "gitmodules", "editorconfig", "env", "cname",
    )

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun extension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase(Locale.ROOT)
    }

    fun baseName(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0) name else name.substring(0, i)
    }

    fun kindOf(name: String, isDir: Boolean): FileKind {
        if (isDir) return FileKind.FOLDER
        val e = extension(name)
        val base = name.substringAfterLast('/').lowercase(Locale.ROOT).removePrefix(".")
        return when {
            e in image -> FileKind.IMAGE
            e in video -> FileKind.VIDEO
            e in audio -> FileKind.AUDIO
            e == "apk" -> FileKind.APK
            e in archive -> FileKind.ARCHIVE
            e == "pdf" -> FileKind.PDF
            e in text -> FileKind.TEXT
            e in doc -> FileKind.DOCUMENT
            e.isEmpty() && base in textFileNames -> FileKind.TEXT
            else -> FileKind.OTHER
        }
    }

    /** Archive formats that the built-in ZIP manager can really open. */
    fun isZip(name: String): Boolean = extension(name).let { it == "zip" || it == "jar" }

    fun isSymlink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    /** True when something (even a broken symlink) exists at this path. */
    fun present(f: File): Boolean = f.exists() || isSymlink(f)

    fun sameFile(a: File, b: File): Boolean = try {
        a.canonicalFile == b.canonicalFile
    } catch (e: Exception) {
        false
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!present(f)) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!present(f)) return f
            i++
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun formatDate(ms: Long): String = if (ms <= 0) "" else synchronized(dateFmt) { dateFmt.format(Date(ms)) }

    fun formatDateTime(ms: Long): String = if (ms <= 0) "-" else synchronized(dateTimeFmt) { dateTimeFmt.format(Date(ms)) }

    fun mime(f: File): String {
        val ext = extension(f.name)
        val m = if (ext.isEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (m != null) return m
        return if (kindOf(f.name, false) == FileKind.TEXT) "text/plain" else "application/octet-stream"
    }
}

object Intents {
    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    /** Returns an error message, or null when the chooser was launched. */
    fun open(ctx: Context, f: File): String? = try {
        val uri = uriFor(ctx, f)
        val mime = FileUtils.mime(f).let { if (it == "application/octet-stream") "*/*" else it }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: ActivityNotFoundException) {
        "No installed app can open this file"
    } catch (e: Exception) {
        e.message ?: "Cannot open this file"
    }

    fun share(ctx: Context, files: List<File>): String? {
        val fs = files.filter { it.isFile }
        if (fs.isEmpty()) return "Only files can be shared. Compress folders into a ZIP first."
        return try {
            val uris = ArrayList(fs.map { uriFor(ctx, it) })
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = FileUtils.mime(fs[0])
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (e: Exception) {
            e.message ?: "Cannot share"
        }
    }

    fun install(ctx: Context, apk: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return "Allow \"Install unknown apps\" for NH Files, then tap Install again."
            }
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(ctx, apk), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            null
        } catch (e: Exception) {
            e.message ?: "Cannot start the installer"
        }
    }
}
