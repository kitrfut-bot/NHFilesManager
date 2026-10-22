package com.nhdeveloper.nhfiles.vpo.ui

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.vpo.data.VideoItem
import com.nhdeveloper.nhfiles.databinding.ItemRowBinding
import com.nhdeveloper.nhfiles.vpo.player.PlayerLauncher
import com.nhdeveloper.nhfiles.vpo.util.formatDuration
import com.nhdeveloper.nhfiles.vpo.util.formatSize
import kotlinx.coroutines.launch

fun View.fadeVisible(show: Boolean) {
    if (show) {
        if (visibility != View.VISIBLE) {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(220).start()
        }
    } else {
        animate().cancel()
        alpha = 1f
        visibility = View.GONE
    }
}

/** Gentle entrance for static pages (settings, more, about). */
fun View.enterAnim() {
    alpha = 0f
    translationY = 24f
    animate().alpha(1f).translationY(0f).setDuration(280).start()
}

fun Fragment.setScreenTitle(title: String) {
    (activity as? AppCompatActivity)?.supportActionBar?.title = title
}

fun LinearLayout.addHeader(text: String) {
    val t = TextView(context)
    t.text = text
    t.setTextColor(context.getColor(R.color.orange))
    t.textSize = 13f
    t.setPadding(48, 40, 48, 8)
    addView(t)
}

fun LinearLayout.addRow(@DrawableRes icon: Int, title: String, summary: String? = null, onClick: () -> Unit): View {
    val b = ItemRowBinding.inflate(LayoutInflater.from(context), this, false)
    if (icon != 0) b.rowIcon.setImageResource(icon) else b.rowIcon.visibility = View.INVISIBLE
    b.rowTitle.text = title
    b.rowSummary.isVisible = summary != null
    b.rowSummary.text = summary
    b.root.setOnClickListener { onClick() }
    addView(b.root)
    return b.root
}

fun LinearLayout.addSwitchRow(@DrawableRes icon: Int, title: String, summary: String?, checked: Boolean,
                              onChange: (Boolean) -> Unit) {
    val b = ItemRowBinding.inflate(LayoutInflater.from(context), this, false)
    if (icon != 0) b.rowIcon.setImageResource(icon) else b.rowIcon.visibility = View.INVISIBLE
    b.rowTitle.text = title
    b.rowSummary.isVisible = summary != null
    b.rowSummary.text = summary
    b.rowSwitch.isVisible = true
    b.rowSwitch.isChecked = checked
    b.rowSwitch.contentDescription = title
    b.rowSwitch.setOnCheckedChangeListener { _, c -> onChange(c) }
    b.root.setOnClickListener { b.rowSwitch.toggle() }
    addView(b.root)
}

fun promptText(ctx: Context, title: String, initial: String, ok: String, onOk: (String) -> Unit) {
    val input = EditText(ctx)
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    input.setText(initial)
    input.setSelection(initial.length)
    input.setTextColor(ctx.getColor(R.color.text_primary))
    val pad = (20 * ctx.resources.displayMetrics.density).toInt()
    val frame = android.widget.FrameLayout(ctx)
    frame.setPadding(pad, pad / 2, pad, 0)
    frame.addView(input)
    MaterialAlertDialogBuilder(ctx).setTitle(title).setView(frame)
        .setPositiveButton(ok) { _, _ ->
            val s = input.text.toString().trim()
            if (s.isNotEmpty()) onOk(s)
        }
        .setNegativeButton("Cancel", null).show()
}

object VideoMenu {
    fun show(
        f: Fragment, vm: LibraryViewModel, anchor: View, v: VideoItem, favorites: Set<String>,
        list: List<VideoItem>, mode: String, key: String
    ) {
        val ctx = f.requireContext()
        val isFav = v.uri in favorites
        val pm = PopupMenu(ctx, anchor)
        pm.menu.add(0, 1, 0, "Play")
        pm.menu.add(0, 2, 1, if (isFav) "Remove from favorites" else "Add to favorites")
        pm.menu.add(0, 3, 2, "Add to playlist")
        if (mode == VideoListFragment.MODE_PLAYLIST) pm.menu.add(0, 4, 3, "Remove from playlist")
        if (mode == VideoListFragment.MODE_HISTORY) pm.menu.add(0, 5, 3, "Remove from history")
        pm.menu.add(0, 6, 4, "Share")
        pm.menu.add(0, 7, 5, "Open with…")
        pm.menu.add(0, 8, 6, "Info")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> PlayerLauncher.play(ctx, list, list.indexOf(v).coerceAtLeast(0))
                2 -> vm.toggleFavorite(v, isFav)
                3 -> addToPlaylist(f, vm, v)
                4 -> vm.removeFromPlaylist(key.toLong(), v)
                5 -> vm.removeHistory(v)
                6 -> share(ctx, v)
                7 -> openWith(ctx, v)
                8 -> info(ctx, v)
            }
            true
        }
        pm.show()
    }

    fun addToPlaylist(f: Fragment, vm: LibraryViewModel, v: VideoItem) {
        f.viewLifecycleOwner.lifecycleScope.launch {
            val lists = vm.playlistsOnce()
            val names = (lists.map { it.name } + "＋ New playlist…").toTypedArray()
            MaterialAlertDialogBuilder(f.requireContext()).setTitle("Add to playlist")
                .setItems(names) { _, i ->
                    if (i < lists.size) {
                        vm.addToPlaylist(lists[i].id, v)
                        Toast.makeText(f.requireContext(), "Added to ${lists[i].name}", Toast.LENGTH_SHORT).show()
                    } else promptText(f.requireContext(), "New playlist", "", "Create") { vm.createPlaylist(it, v) }
                }.show()
        }
    }

    fun share(ctx: Context, v: VideoItem) {
        val i = Intent(Intent.ACTION_SEND).setType("video/*")
            .putExtra(Intent.EXTRA_STREAM, v.parsedUri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(i, "Share video")) }
        catch (_: Exception) { Toast.makeText(ctx, "No app can share this file", Toast.LENGTH_SHORT).show() }
    }

    fun openWith(ctx: Context, v: VideoItem) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(v.parsedUri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(i, "Open with")) }
        catch (_: Exception) { Toast.makeText(ctx, "No app found", Toast.LENGTH_SHORT).show() }
    }

    fun info(ctx: Context, v: VideoItem) {
        val res = if (v.width > 0) "${v.width} × ${v.height}" else "Unknown"
        val msg = "Name: ${v.name}\nFolder: ${v.folderName}\nPath: ${v.folderPath.ifEmpty { "—" }}\n" +
            "Duration: ${formatDuration(v.durationMs)}\nResolution: $res\nSize: ${formatSize(v.size)}"
        MaterialAlertDialogBuilder(ctx).setTitle("Video info").setMessage(msg)
            .setPositiveButton("OK", null).show()
    }
}
