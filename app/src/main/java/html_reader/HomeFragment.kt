package com.html_reader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.data.repo.NetworkConfigRepository
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var recentsButton: Button
    private lateinit var favoritesButton: Button
    private lateinit var localList: ListView
    private lateinit var sdCardList: ListView
    private lateinit var networkList: ListView
    
    private lateinit var networkConfigRepository: NetworkConfigRepository
    
    private val localDirs = mutableListOf<String>()
    private val sdCardDirs = mutableListOf<String>()
    private val networkConfigs = mutableListOf<NetworkConfigEntity>()

    private val safPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
            (activity as? MainActivity)?.showDirectoryModeWithSafTree(uri.toString())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        networkConfigRepository = FilesRuntime.networkConfigRepository(context)

        recentsButton = view.findViewById(R.id.home_recents_button)
        favoritesButton = view.findViewById(R.id.home_favorites_button)
        localList = view.findViewById(R.id.home_local_list)
        sdCardList = view.findViewById(R.id.home_sd_list)
        networkList = view.findViewById(R.id.home_network_list)

        recentsButton.setOnClickListener {
            (activity as? MainActivity)?.showRecentsPage()
        }
        
        favoritesButton.setOnClickListener {
            (activity as? MainActivity)?.showFavoritesPage()
        }

        // Initialize Local List (Standard directories)
        val defaultLocal = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
        localDirs.add(getString(R.string.home_local_downloads))
        localList.adapter = ArrayAdapter(context, R.layout.item_home_rect, localDirs)
        localList.setOnItemClickListener { _, _, _, _ ->
            (activity as? MainActivity)?.showDirectoryModeWithPath(defaultLocal)
        }

        checkAndRequestStoragePermission()

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
        val addSdCardLabel = getString(R.string.home_sd_add_saf)
        if (sdCardDirs.isEmpty()) {
            sdCardDirs.add(addSdCardLabel)
        }
        sdCardList.adapter = ArrayAdapter(context, R.layout.item_home_rect, sdCardDirs)
        sdCardList.setOnItemClickListener { _, _, position, _ ->
            val path = sdCardDirs[position]
            if (path == addSdCardLabel) {
                safPicker.launch(null)
            } else {
                (activity as? MainActivity)?.showDirectoryModeWithPath(path)
            }
        }

        // Initialize Network List
        val netAdapter = ArrayAdapter(context, R.layout.item_home_rect, mutableListOf<String>())
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
                display.add(getString(R.string.home_network_add_hint))
                netAdapter.clear()
                netAdapter.addAll(display)
                netAdapter.notifyDataSetChanged()
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
            listOf(
                getString(R.string.home_network_type_smb),
                getString(R.string.home_network_type_ftp)
            )
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.home_network_add_title)
            .setView(view)
            .setPositiveButton(R.string.home_action_save) { _, _ ->
                val name = nameInput.text.toString()
                val host = hostInput.text.toString()
                val port = portInput.text.toString().toIntOrNull() ?: 0
                val user = userInput.text.toString()
                val pass = passInput.text.toString()
                val protocol = if (typeSpinner.selectedItemPosition == 0) NetworkProtocol.SMB else NetworkProtocol.FTP
                
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
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun checkAndRequestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.home_permission_required_title)
                    .setMessage(R.string.home_permission_required_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        startActivity(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
