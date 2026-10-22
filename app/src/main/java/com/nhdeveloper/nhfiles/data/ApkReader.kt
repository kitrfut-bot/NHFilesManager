package com.nhdeveloper.nhfiles.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

data class CertInfo(
    val subject: String,
    val issuer: String,
    val sha256: String,
    val validFrom: String,
    val validTo: String,
)

data class ApkInfo(
    val fileName: String,
    val sizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val label: String,
    val minSdk: Int,
    val targetSdk: Int,
    val icon: Bitmap?,
    val certs: List<CertInfo>,
    val permissions: List<String>,
    val entries: List<ZipEntryInfo>,
)

object ApkReader {

    @Suppress("DEPRECATION")
    fun read(ctx: Context, apk: File): ApkInfo {
        val pm = ctx.packageManager
        val sigFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val flags = sigFlag or PackageManager.GET_PERMISSIONS
        val pi = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IOException("This file is not a valid or readable APK")
        val ai = pi.applicationInfo ?: throw IOException("The APK has no application information")
        ai.sourceDir = apk.absolutePath
        ai.publicSourceDir = apk.absolutePath

        val label = try {
            ai.loadLabel(pm).toString()
        } catch (e: Exception) {
            pi.packageName
        }
        val icon = try {
            ai.loadIcon(pm).toBitmap(192, 192)
        } catch (e: Exception) {
            null
        }

        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val si = pi.signingInfo
            if (si == null) null else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            pi.signatures
        }
        val certs = ArrayList<CertInfo>()
        if (sigs != null) {
            val cf = CertificateFactory.getInstance("X.509")
            for (s in sigs) {
                try {
                    val c = cf.generateCertificate(ByteArrayInputStream(s.toByteArray())) as X509Certificate
                    val digest = MessageDigest.getInstance("SHA-256").digest(c.encoded)
                    certs.add(
                        CertInfo(
                            subject = c.subjectX500Principal.name,
                            issuer = c.issuerX500Principal.name,
                            sha256 = digest.joinToString(":") { "%02X".format(it) },
                            validFrom = FileUtils.formatDateTime(c.notBefore.time),
                            validTo = FileUtils.formatDateTime(c.notAfter.time),
                        )
                    )
                } catch (e: Exception) {
                    // Skip a certificate that cannot be parsed.
                }
            }
        }

        val entries = try {
            ZipFile(apk).use { z ->
                val out = ArrayList<ZipEntryInfo>()
                val en = z.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    out.add(ZipEntryInfo(e.name, e.isDirectory, e.size, e.compressedSize, e.time))
                }
                out
            }
        } catch (e: Exception) {
            emptyList()
        }

        return ApkInfo(
            fileName = apk.name,
            sizeBytes = apk.length(),
            packageName = pi.packageName,
            versionName = pi.versionName ?: "-",
            versionCode = PackageInfoCompat.getLongVersionCode(pi),
            label = label,
            minSdk = ai.minSdkVersion,
            targetSdk = ai.targetSdkVersion,
            icon = icon,
            certs = certs,
            permissions = (pi.requestedPermissions ?: emptyArray()).toList(),
            entries = entries,
        )
    }

    fun androidName(sdk: Int): String = when (sdk) {
        26 -> "Android 8.0"; 27 -> "Android 8.1"; 28 -> "Android 9"; 29 -> "Android 10"
        30 -> "Android 11"; 31 -> "Android 12"; 32 -> "Android 12L"; 33 -> "Android 13"
        34 -> "Android 14"; 35 -> "Android 15"; 36 -> "Android 16"
        else -> "API $sdk"
    }
}
