package com.nhdeveloper.nhfiles.vpo.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.databinding.FragmentHomeBinding
import com.nhdeveloper.nhfiles.vpo.player.PlayerLauncher
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val vm: LibraryViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHomeBinding.bind(view)
        b.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> VideoListFragment.newInstance(VideoListFragment.MODE_ALL)
                1 -> FoldersFragment()
                else -> PlaylistsFragment()
            }
        }
        b.pager.offscreenPageLimit = 2
        b.pager.setPageTransformer { page, pos ->
            val a = kotlin.math.abs(pos).coerceIn(0f, 1f)
            page.alpha = 1f - 0.45f * a
            val s = 1f - 0.05f * a
            page.scaleX = s; page.scaleY = s
        }
        val names = arrayOf("Videos", "Folders", "Playlists")
        TabLayoutMediator(b.tabs, b.pager) { tab, pos -> tab.text = names[pos] }.attach()

        val adapter = ContinueAdapter { c ->
            PlayerLauncher.play(requireContext(), listOf(c.video), 0)
        }
        b.continueList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.continueList.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.continueWatching.collect {
                    adapter.submitList(it)
                    b.continueSection.fadeVisible(it.isNotEmpty())
                }
            }
        }
    }

    override fun onResume() { super.onResume(); setScreenTitle("Video Player Offline") }
}
