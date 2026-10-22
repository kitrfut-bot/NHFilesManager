package com.nhdeveloper.nhfiles.vpo.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.databinding.FragmentRowsBinding

class MoreFragment : Fragment(R.layout.fragment_rows) {
    private val vm: LibraryViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rows = FragmentRowsBinding.bind(view).rows
        rows.enterAnim()
        val main = activity as MainActivity
        rows.addRow(R.drawable.ic_favorite, "Favorites", "Videos you starred") {
            main.push(VideoListFragment.newInstance(VideoListFragment.MODE_FAVORITES, "", "Favorites"))
        }
        rows.addRow(R.drawable.ic_history, "History", "Recently played, resume positions") {
            main.push(VideoListFragment.newInstance(VideoListFragment.MODE_HISTORY, "", "History"))
        }
        rows.addRow(R.drawable.ic_folder_add, "Add folder", "Grant access to a folder (SD card, USB…)") { main.pickFolder() }
        rows.addRow(R.drawable.ic_refresh, "Rescan library") { vm.refresh() }
        rows.addRow(R.drawable.ic_settings, "Settings") { main.push(SettingsFragment()) }
        rows.addRow(R.drawable.ic_info, "About") { main.push(AboutFragment()) }
    }

    override fun onResume() { super.onResume(); setScreenTitle("More") }
}
