package com.html_reader

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var isUserNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val mode = getSharedPreferences(settingsPrefsName, MODE_PRIVATE)
            .getInt(themeModeKey, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bottomNav = findViewById(R.id.main_bottom_nav)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = getCurrentVisibleFragment()
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
            if (isProgrammaticSelection) {
                return@setOnItemSelectedListener true
            }
            isUserNavigation = true
            try {
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
            } finally {
                isUserNavigation = false
            }
            true
        }

        bottomNav.setOnItemReselectedListener { item ->
            when (item.itemId) {
                R.id.nav_files -> {
                    val current = getCurrentVisibleFragment()
                    if (current is FoldersOverviewFragment) {
                        when {
                            supportFragmentManager.findFragmentByTag("directory_mode_folders") != null -> {
                                switchFragment("directory_mode_folders", { FilesFragment() }, forceReplace = false)
                            }
                            supportFragmentManager.findFragmentByTag("directory_mode") != null -> {
                                switchFragment("directory_mode", { FilesFragment() }, forceReplace = false)
                            }
                            else -> {
                                showContent(FilesFragment(), "directory_mode_folders")
                            }
                        }
                    } else {
                        showOverview(FoldersOverviewFragment(), "folders_overview")
                    }
                }
                R.id.nav_reader -> {
                    val current = getCurrentVisibleFragment()
                    if (current is TabsOverviewFragment) {
                        if (supportFragmentManager.findFragmentByTag("reader_mode") != null) {
                            switchFragment("reader_mode", { ReaderFragment() }, forceReplace = false)
                        } else {
                            showContent(ReaderFragment(), "reader_mode")
                        }
                    } else {
                        showOverview(TabsOverviewFragment(), "tabs_overview")
                    }
                }
                R.id.nav_home -> {
                    showHomeRoot()
                }
            }
        }
    }

    private fun showHomeRoot() {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val current = getCurrentVisibleFragment()
        if (current is HomeFragment) {
            syncBottomNavSelection()
            return
        }
        switchFragment("home_root", { HomeFragment() }, forceReplace = false)
    }

    private fun showOverview(fragment: Fragment, tag: String) {
        if (tag == "folders_overview") {
            lastFoldersTag = tag
        } else if (tag == "tabs_overview") {
            lastReaderTag = tag
        }
        val currentTag = getCurrentVisibleFragment()?.tag
        if (currentTag == tag) {
            syncBottomNavSelection()
            return
        }
        switchFragment(tag, { fragment }, forceReplace = false)
    }

    fun showFavoritesPage() {
        switchFragment("favorites_page", { FavoritesFragment() }, forceReplace = true)
    }

    fun showDirectoryMode(fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        switchFragment(tag, { FilesFragment() }, forceReplace = false)
    }

    fun showDirectoryModeWithPath(path: String, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        switchFragment(tag, { FilesFragment.newInstanceForPath(path) }, forceReplace = true)
    }

    fun showDirectoryModeWithSafTree(treeUri: String, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        switchFragment(tag, { FilesFragment.newInstanceForSafTree(treeUri) }, forceReplace = true)
    }

    fun showDirectoryModeWithNetwork(networkConfigId: Long, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        switchFragment(tag, { FilesFragment.newInstance(networkConfigId) }, forceReplace = true)
    }

    fun showDirectoryModeWithNetworkPath(networkConfigId: Long, startPath: String, fromFolders: Boolean = false) {
        val tag = if (fromFolders) "directory_mode_folders" else "directory_mode"
        if (tag == "directory_mode_folders") lastFoldersTag = tag
        switchFragment(tag, { FilesFragment.newInstanceForNetworkPath(networkConfigId, startPath) }, forceReplace = true)
    }

    fun showReaderMode() {
        lastReaderTag = "reader_mode"
        switchFragment("reader_mode", { ReaderFragment() }, forceReplace = false)
    }

    fun showReaderModeWithPath(path: String) {
        lastReaderTag = "reader_mode"
        switchFragment("reader_mode", { ReaderFragment.newInstance(path) }, forceReplace = true)
    }

    fun showMorePage() {
        showOverview(MoreFragment(), "more_overview")
    }

    fun showRecentsPage() {
        switchFragment("recents_page", { RecentsFragment() }, forceReplace = true)
    }

    private fun showContent(fragment: Fragment, tag: String) {
        switchFragment(tag, { fragment }, forceReplace = false)
    }

    private fun switchFragment(tag: String, create: () -> Fragment, forceReplace: Boolean = false) {
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()

        // 1. Hide all visible fragments
        fm.fragments.forEach {
            if (it.isVisible) transaction.hide(it)
        }

        // 2. Find or create target
        var target = fm.findFragmentByTag(tag)

        if (target != null && forceReplace) {
            transaction.remove(target)
            target = null
        }

        if (target == null) {
            target = create()
            transaction.add(R.id.main_content, target, tag)
        } else {
            transaction.show(target)
        }

        transaction.commit()
        syncBottomNavSelection(tag)
    }

    private fun syncBottomNavSelection(explicitTag: String? = null) {
        val current = getCurrentVisibleFragment()
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

    private fun getCurrentVisibleFragment(): Fragment? {
        return supportFragmentManager.fragments.lastOrNull { it.isVisible && !it.isHidden }
    }
}
