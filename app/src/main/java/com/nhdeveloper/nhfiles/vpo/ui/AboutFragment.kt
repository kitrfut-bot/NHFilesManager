package com.nhdeveloper.nhfiles.vpo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.databinding.FragmentRowsBinding

class AboutFragment : Fragment(R.layout.fragment_rows) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rows = FragmentRowsBinding.bind(view).rows
        rows.enterAnim()
        val ctx = requireContext()
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "1.0" }
        rows.addRow(R.drawable.ic_movie, "Video Player Offline", "Version $version") { }
        rows.addRow(R.drawable.ic_info, "100% offline",
            "No internet permission, no ads, no analytics, no account. Everything stays on this device.") { }
        rows.addRow(R.drawable.ic_play, "Playback engine",
            "AndroidX Media3 ExoPlayer with FFmpeg audio decoders (AC-3, E-AC-3, DTS, TrueHD, AAC, MP3, Opus, FLAC…). Video is decoded by your phone's hardware, so very exotic video codecs may not play on every device.") { }
        rows.addRow(R.drawable.ic_info, "Developer", "NH Developer") { }
        rows.addRow(R.drawable.ic_open, "Telegram", "t.me/htmlmovievg") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/htmlmovievg"))) }
            catch (_: Exception) { Toast.makeText(ctx, "No app to open link", Toast.LENGTH_SHORT).show() }
        }
        rows.addRow(R.drawable.ic_favorite, "Rate us") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}"))) }
            catch (_: Exception) { Toast.makeText(ctx, "Store not available", Toast.LENGTH_SHORT).show() }
        }
        rows.addRow(R.drawable.ic_info, "Open-source libraries",
            "AndroidX Media3 (Apache 2.0), Jellyfin Media3 FFmpeg decoder (GPL-3.0), Glide (BSD/Apache), Room, DataStore, Material Components (Apache 2.0).") { }
    }

    override fun onResume() { super.onResume(); setScreenTitle("About") }
}
