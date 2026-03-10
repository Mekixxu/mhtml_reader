package com.html_reader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.data.repo.FavoritesRepository
import core.data.repo.HistoryRepository
import core.data.repo.NetworkConfigRepository
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var recentsButton: Button
    private lateinit var localList: ListView
    private lateinit var sdCardList: ListView
    private lateinit var networkList: ListView
    private lateinit var favoritesShortcutList: ListView
    
    private lateinit var historyRepository: HistoryRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var networkConfigRepository: NetworkConfigRepository
    
    private val localDirs = mutableListOf<String>()
    private val sdCardDirs = mutableListOf<String>()
    private val networkConfigs = mutableListOf<NetworkConfigEntity>()
    private val favoritesShortcuts = mutableListOf<core.database.entity.FavoriteEntity>()

    private val safPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
            (activity as? MainActivity)?.showDirectoryModeWithSafTree(uri.toString())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        historyRepository = ReaderRuntime.historyRepository(context)
        favoritesRepository = FilesRuntime.favoritesRepository(context)
        networkConfigRepository = FilesRuntime.networkConfigRepository(context)

        recentsButton = view.findViewById(R.id.home_recents_button)
        localList = view.findViewById(R.id.home_local_list)
        sdCardList = view.findViewById(R.id.home_sd_list)
        networkList = view.findViewById(R.id.home_network_list)
        favoritesShortcutList = view.findViewById(R.id.home_favorites_list)

        recentsButton.setOnClickListener {
            (activity as? MainActivity)?.showRecentsPage()
        }

        // Initialize Local List (Standard directories)
        val defaultLocal = requireContext().filesDir.parentFile?.absolutePath ?: "/"
        localDirs.add(defaultLocal)
        localList.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, localDirs)
        localList.setOnItemClickListener { _, _, _, _ ->
            (activity as? MainActivity)?.showDirectoryModeWithPath(defaultLocal)
        }

        // Initialize SD Card List (Check external storage)
        val externalDirs = context.getExternalFilesDirs(null)
        sdCardDirs.clear()
        if (externalDirs.size > 1) {
            // Usually index 1 is SD card
            externalDirs.drop(1).filterNotNull().forEach { file ->
                // Try to find root of SD card from app private dir
                // Path usually: /storage/XXXX-XXXX/Android/data/pkg/files
                val root = file.absolutePath.substringBefore("/Android/data")
                sdCardDirs.add(root)
            }
        }
        if (sdCardDirs.isEmpty()) {
            sdCardDirs.add("Add SD Card (SAF)")
        }
        sdCardList.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, sdCardDirs)
        sdCardList.setOnItemClickListener { _, _, position, _ ->
            val path = sdCardDirs[position]
            if (path == "Add SD Card (SAF)") {
                safPicker.launch(null)
            } else {
                (activity as? MainActivity)?.showDirectoryModeWithPath(path)
            }
        }

        // Initialize Network List
        val netAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf<String>())
        networkList.adapter = netAdapter
        networkList.setOnItemClickListener { _, _, position, _ ->
            if (position == networkConfigs.size) {
                // Add new
                showAddNetworkDialog()
            } else {
                val config = networkConfigs[position]
                (activity as? MainActivity)?.showDirectoryModeWithNetwork(config.id)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            networkConfigRepository.observeAll().collect { configs ->
                networkConfigs.clear()
                networkConfigs.addAll(configs)
                val display = configs.map { "${it.protocol.name}: ${it.name} (${it.host})" }.toMutableList()
                display.add("+ Add Network Connection")
                netAdapter.clear()
                netAdapter.addAll(display)
                netAdapter.notifyDataSetChanged()
            }
        }

        // Initialize Favorites Shortcuts
        val favAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf<String>())
        favoritesShortcutList.adapter = favAdapter
        favoritesShortcutList.setOnItemClickListener { _, _, position, _ ->
            val item = favoritesShortcuts.getOrNull(position) ?: return@setOnItemClickListener
            when (item.sourceType) {
                SourceType.LOCAL -> (activity as? MainActivity)?.showReaderModeWithPath(item.path)
                SourceType.FTP -> { /* TODO: Open FTP favorite */ }
                SourceType.SMB -> { /* TODO: Open SMB favorite */ }
                else -> {}
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            favoritesRepository.observeAll().collect { allFavs ->
                favoritesShortcuts.clear()
                favoritesShortcuts.addAll(allFavs.take(5)) // Show top 5
                val display = favoritesShortcuts.map { "★ ${it.name}" }
                favAdapter.clear()
                favAdapter.addAll(display)
                favAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showAddNetworkDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_network, null)
        val nameInput = view.findViewById<EditText>(R.id.net_name)
        val hostInput = view.findViewById<EditText>(R.id.net_host)
        val portInput = view.findViewById<EditText>(R.id.net_port)
        val userInput = view.findViewById<EditText>(R.id.net_user)
        val passInput = view.findViewById<EditText>(R.id.net_pass)
        val typeSpinner = view.findViewById<Spinner>(R.id.net_type)
        
        typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("SMB", "FTP")
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Add Network Connection")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString()
                val host = hostInput.text.toString()
                val port = portInput.text.toString().toIntOrNull() ?: 0
                val user = userInput.text.toString()
                val pass = passInput.text.toString()
                val typeStr = typeSpinner.selectedItem.toString()
                val protocol = if (typeStr == "SMB") NetworkProtocol.SMB else NetworkProtocol.FTP
                
                if (name.isNotBlank() && host.isNotBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        networkConfigRepository.add(
                            NetworkConfigEntity(
                                name = name,
                                protocol = protocol,
                                host = host,
                                port = if (port > 0) port else if (protocol == NetworkProtocol.FTP) 21 else 445,
                                username = user,
                                password = pass,
                                defaultPath = "/"
                            )
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
