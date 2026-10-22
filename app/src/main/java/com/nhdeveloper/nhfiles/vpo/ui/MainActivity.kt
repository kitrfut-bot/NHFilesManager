package com.nhdeveloper.nhfiles.vpo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.databinding.ActivityMainBinding
import com.nhdeveloper.nhfiles.vpo.util.hasVideoPermission
import com.nhdeveloper.nhfiles.vpo.util.videoPermission

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    val vm: LibraryViewModel by viewModels()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refresh()
        else {
            vm.refresh()
            if (!shouldShowRequestPermissionRationale(videoPermission())) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    private val treeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            vm.addTree(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportFragmentManager.addOnBackStackChangedListener { updateNav() }

        b.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> showRoot(HomeFragment())
                R.id.nav_folders -> showRoot(FoldersFragment())
                R.id.nav_playlists -> showRoot(PlaylistsFragment())
                else -> showRoot(MoreFragment())
            }
            true
        }
        b.bottomNav.setOnItemReselectedListener { }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = R.id.nav_home

        if (hasVideoPermission(this)) vm.refresh() else permLauncher.launch(videoPermission())
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh) { vm.refresh(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun updateNav() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            b.toolbar.setNavigationIcon(R.drawable.ic_back)
            b.toolbar.navigationContentDescription = "Back"
        } else b.toolbar.navigationIcon = null
    }

    private fun showRoot(f: Fragment) {
        if (supportFragmentManager.backStackEntryCount > 0)
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.container, f).commit()
    }

    fun push(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            .replace(R.id.container, f).addToBackStack(null).commit()
    }

    fun requestVideoPermission() = permLauncher.launch(videoPermission())
    fun pickFolder() = treeLauncher.launch(null)
}
