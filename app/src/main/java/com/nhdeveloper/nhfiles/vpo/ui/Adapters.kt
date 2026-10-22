package com.nhdeveloper.nhfiles.vpo.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.vpo.data.ContinueItem
import com.nhdeveloper.nhfiles.vpo.data.FolderItem
import com.nhdeveloper.nhfiles.vpo.data.PlaylistRow
import com.nhdeveloper.nhfiles.vpo.data.VideoItem
import com.nhdeveloper.nhfiles.databinding.ItemContinueBinding
import com.nhdeveloper.nhfiles.databinding.ItemFolderBinding
import com.nhdeveloper.nhfiles.databinding.ItemVideoBinding
import com.nhdeveloper.nhfiles.vpo.util.formatDuration
import com.nhdeveloper.nhfiles.vpo.util.metaLine

private fun loadThumb(view: android.widget.ImageView, v: VideoItem) {
    Glide.with(view).load(v.parsedUri)
        .apply(RequestOptions.frameOf(1_000_000L).centerCrop().diskCacheStrategy(DiskCacheStrategy.RESOURCE))
        .override(240, 136)
        .transition(DrawableTransitionOptions.withCrossFade(180))
        .placeholder(R.drawable.ic_movie).error(R.drawable.ic_movie)
        .into(view)
}

class VideoAdapter(
    private val onClick: (List<VideoItem>, Int) -> Unit,
    private val onMore: (View, VideoItem) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VH>(object : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(a: VideoItem, b: VideoItem) = a.uri == b.uri
    override fun areContentsTheSame(a: VideoItem, b: VideoItem) = a == b
}) {
    var favorites: Set<String> = emptySet()
        set(value) { if (field == value) return; field = value; notifyItemRangeChanged(0, itemCount, "fav") }

    inner class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) { h.b.favMark.isVisible = getItem(pos).uri in favorites; return }
        super.onBindViewHolder(h, pos, payloads)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val v = getItem(pos)
        h.b.title.text = v.title
        h.b.meta.text = v.metaLine()
        h.b.duration.text = formatDuration(v.durationMs)
        h.b.duration.isVisible = v.durationMs > 0
        h.b.favMark.isVisible = v.uri in favorites
        loadThumb(h.b.thumb, v)
        h.b.root.setOnClickListener { onClick(currentList, h.bindingAdapterPosition) }
        h.b.more.setOnClickListener { onMore(it, v) }
    }
}

class ContinueAdapter(private val onClick: (ContinueItem) -> Unit) :
    ListAdapter<ContinueItem, ContinueAdapter.VH>(object : DiffUtil.ItemCallback<ContinueItem>() {
        override fun areItemsTheSame(a: ContinueItem, b: ContinueItem) = a.video.uri == b.video.uri
        override fun areContentsTheSame(a: ContinueItem, b: ContinueItem) = a == b
    }) {
    inner class VH(val b: ItemContinueBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemContinueBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = getItem(pos)
        h.b.title.text = c.video.title
        h.b.progress.progress = c.progress
        loadThumb(h.b.thumb, c.video)
        h.b.root.contentDescription = "Continue ${c.video.title} from ${formatDuration(c.positionMs)}"
        h.b.root.setOnClickListener { onClick(c) }
    }
}

class FolderAdapter(private val onClick: (FolderItem) -> Unit) :
    ListAdapter<FolderItem, FolderAdapter.VH>(object : DiffUtil.ItemCallback<FolderItem>() {
        override fun areItemsTheSame(a: FolderItem, b: FolderItem) = a.key == b.key
        override fun areContentsTheSame(a: FolderItem, b: FolderItem) = a == b
    }) {
    inner class VH(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemFolderBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = getItem(pos)
        h.b.name.text = f.name
        h.b.count.text = "${f.count} video" + if (f.count == 1) "" else "s"
        h.b.root.setOnClickListener { onClick(f) }
    }
}

class PlaylistAdapter(
    private val onClick: (PlaylistRow) -> Unit,
    private val onMore: (View, PlaylistRow) -> Unit
) : ListAdapter<PlaylistRow, PlaylistAdapter.VH>(object : DiffUtil.ItemCallback<PlaylistRow>() {
    override fun areItemsTheSame(a: PlaylistRow, b: PlaylistRow) = a.id == b.id
    override fun areContentsTheSame(a: PlaylistRow, b: PlaylistRow) = a == b
}) {
    inner class VH(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemFolderBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val r = getItem(pos)
        h.b.icon.setImageResource(R.drawable.ic_playlist)
        h.b.name.text = r.name
        h.b.count.text = "${r.itemCount} video" + if (r.itemCount == 1) "" else "s"
        h.b.more.isVisible = true
        h.b.chevron.isVisible = false
        h.b.root.setOnClickListener { onClick(r) }
        h.b.more.setOnClickListener { onMore(it, r) }
    }
}
