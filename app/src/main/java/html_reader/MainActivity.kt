package com.html_reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private val settingsPrefsName = "app_settings"
    private val themeModeKey = "theme_mode"
    private var lastFoldersTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = getSharedPreferences(settingsPrefsName, MODE_PRIVATE)
            .getInt(themeModeKey, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.main_bottom_nav)
        supportFragmentManager.addOnBackStackChangedListener {
            syncBottomNavSelection()
        }
        if (savedInstanceState == null) {
            showHomeRoot()
        }
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showHomeRoot()
                R.id.nav_files -> {
                    // If switching to files tab, try to restore last state
                    // If already on files tab, reset to overview (standard behavior)
                    if (bottomNav.selectedItemId == R.id.nav_files) {
                        showOverview(FoldersOverviewFragment(), "folders_overview")
                    } else {
                        if (lastFoldersTag == "directory_mode_folders") {
                            // Restore the file browser session
                            showContent(FilesFragment(), "directory_mode_folders")
                        } else {
                            showOverview(FoldersOverviewFragment(), "folders_overview")
                        }
                    }
                }
                R.id.nav_reader -> showOverview(TabsOverviewFragment(), "tabs_overview")
                R.id.nav_more -> showOverview(MoreFragment(), "more_overview")
            }
            true
        }
    }

    private fun showHomeRoot() {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val current = supportFragmentManager.findFragmentById(R.id.main_content)
        if (current is HomeFragment) {
            syncBottomNavSelection()
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content, HomeFragment(), "home_root")
            .commit()
        syncBottomNavSelection()
    }

    private fun showOverview(fragment: Fragment, tag: String) {
        if (tag == "folders_overview") {
            lastFoldersTag = tag
        }
        val currentTag = supportFragmentManager.findFragmentById(R.id.main_content)?.tag
        if (currentTag == tag) {
            syncBottomNavSelection()
            return
        }
        showContent(fragment, tag)
    }

    fun showFavoritesPage() {
        showContent(FavoritesFragment(), "favorites_page")
    }

    fun showDirectoryMode(fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        showContent(FilesFragment(), tag)
    }

    fun showDirectoryModeWithPath(path: String, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        showContent(FilesFragment.newInstanceForPath(path), tag)
    }

    fun showDirectoryModeWithSafTree(treeUri: String, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        showContent(FilesFragment.newInstanceForSafTree(treeUri), tag)
    }

    fun showDirectoryModeWithNetwork(networkConfigId: Long, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        showContent(FilesFragment.newInstance(networkConfigId), tag)
    }

    fun showDirectoryModeWithNetworkPath(networkConfigId: Long, startPath: String, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        showContent(FilesFragment.newInstanceForNetworkPath(networkConfigId, startPath), tag)
    }

    fun showReaderMode() {
        showContent(ReaderFragment(), "reader_mode")
    }

    fun showReaderModeWithPath(path: String) {
        showContent(ReaderFragment.newInstance(path), "reader_mode")
    }

    fun showMorePage() {
        showOverview(MoreFragment(), "more_overview")
    }

    private fun showContent(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content, fragment, tag)
            .addToBackStack(tag)
            .commit()
        syncBottomNavSelection()
    }

    private fun syncBottomNavSelection() {
        val current = supportFragmentManager.findFragmentById(R.id.main_content)
        val tag = current?.tag
        val itemId = when {
            tag == "directory_mode_folders" -> R.id.nav_files
            // Also map directory_mode to files, just in case, though we prefer directory_mode_folders now
            tag == "directory_mode" -> R.id.nav_files 
            current is FoldersOverviewFragment -> R.id.nav_files
            current is TabsOverviewFragment -> R.id.nav_reader
            current is MoreFragment -> R.id.nav_more
            else -> R.id.nav_home
        }
        if (bottomNav.selectedItemId != itemId) {
            bottomNav.menu.findItem(itemId)?.isChecked = true
        }
    }
}
