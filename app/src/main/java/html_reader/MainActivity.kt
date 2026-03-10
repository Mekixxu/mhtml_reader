package com.html_reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private val settingsPrefsName = "app_settings"
    private val themeModeKey = "theme_mode"
    private var lastFoldersTag: String? = null
    private var lastReaderTag: String? = null
    private var isProgrammaticSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = getSharedPreferences(settingsPrefsName, MODE_PRIVATE)
            .getInt(themeModeKey, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.main_bottom_nav)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = supportFragmentManager.findFragmentById(R.id.main_content)
                if (current is FilesFragment) {
                    if (!current.navigateUp()) {
                        showOverview(FoldersOverviewFragment(), "folders_overview")
                    }
                    return
                }
                if (current is ReaderFragment) {
                    showOverview(TabsOverviewFragment(), "tabs_overview")
                    return
                }
                if (current is FoldersOverviewFragment || current is TabsOverviewFragment || current is MoreFragment) {
                    bottomNav.selectedItemId = R.id.nav_home
                    return
                }
                // If at Home, exit
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        if (savedInstanceState == null) {
            showHomeRoot()
        }
        
        bottomNav.setOnItemSelectedListener { item ->
            // Switching TO a tab
            when (item.itemId) {
                R.id.nav_home -> showHomeRoot()
                R.id.nav_files -> {
                    if (lastFoldersTag == "directory_mode_folders") {
                        showContent(FilesFragment(), "directory_mode_folders")
                    } else {
                        showOverview(FoldersOverviewFragment(), "folders_overview")
                    }
                }
                R.id.nav_reader -> {
                    if (lastReaderTag == "reader_mode") {
                         showContent(ReaderFragment(), "reader_mode")
                    } else {
                        showOverview(TabsOverviewFragment(), "tabs_overview")
                    }
                }
                R.id.nav_more -> showOverview(MoreFragment(), "more_overview")
            }
            true
        }

        bottomNav.setOnItemReselectedListener { item ->
            // Toggling within the tab
            when (item.itemId) {
                R.id.nav_files -> {
                    val current = supportFragmentManager.findFragmentById(R.id.main_content)
                    if (current is FilesFragment) {
                        showOverview(FoldersOverviewFragment(), "folders_overview")
                    } else {
                        showContent(FilesFragment(), "directory_mode_folders")
                    }
                }
                R.id.nav_reader -> {
                    val current = supportFragmentManager.findFragmentById(R.id.main_content)
                    if (current is ReaderFragment) {
                        showOverview(TabsOverviewFragment(), "tabs_overview")
                    } else {
                        showContent(ReaderFragment(), "reader_mode")
                    }
                }
                R.id.nav_home -> {
                     // Maybe refresh home or scroll to top?
                     showHomeRoot()
                }
            }
        }
    }

    private fun showHomeRoot() {
        // Clear back stack
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
        } else if (tag == "tabs_overview") {
            lastReaderTag = tag
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
        lastReaderTag = "reader_mode"
        showContent(ReaderFragment(), "reader_mode")
    }

    fun showReaderModeWithPath(path: String) {
        lastReaderTag = "reader_mode"
        showContent(ReaderFragment.newInstance(path), "reader_mode")
    }

    fun showMorePage() {
        showOverview(MoreFragment(), "more_overview")
    }

    private fun showContent(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content, fragment, tag)
            .commit()
        syncBottomNavSelection(tag)
    }

    private fun syncBottomNavSelection(explicitTag: String? = null) {
        val current = supportFragmentManager.findFragmentById(R.id.main_content)
        val tag = explicitTag ?: current?.tag
        val itemId = when {
            tag == "directory_mode_folders" || tag == "directory_mode" || tag == "folders_overview" -> R.id.nav_files
            current is FoldersOverviewFragment -> R.id.nav_files
            tag == "reader_mode" || tag == "tabs_overview" || current is TabsOverviewFragment -> R.id.nav_reader
            tag == "more_overview" || current is MoreFragment -> R.id.nav_more
            else -> R.id.nav_home
        }
        if (bottomNav.selectedItemId != itemId) {
            isProgrammaticSelection = true
            bottomNav.selectedItemId = itemId
            isProgrammaticSelection = false
        }
    }
}
