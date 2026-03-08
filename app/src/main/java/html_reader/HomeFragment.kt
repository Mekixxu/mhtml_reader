package com.html_reader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.database.entity.HistoryEntity
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private lateinit var openFavoritesButton: Button
    private lateinit var addAuthorizedDirButton: View
    private lateinit var addNetworkButton: View
    private lateinit var localDirsList: ListView
    private lateinit var recentsList: ListView
    private lateinit var networkList: ListView
    private lateinit var localDirsAdapter: ArrayAdapter<String>
    private lateinit var recentsAdapter: ArrayAdapter<String>
    private lateinit var networkAdapter: ArrayAdapter<String>
    private val localDirs = mutableListOf<HomeLocalEntry>()
    private val recents = mutableListOf<HistoryEntity>()
    private val networkConfigs = mutableListOf<NetworkConfigEntity>()
    private var authorizedDirs: List<AuthorizedDir> = emptyList()
    private val addAuthorizedDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistSafPermission(uri)
            val displayName = deriveTreeDisplayName(uri)
            FilesRuntime.authorizedDirStore(requireContext()).upsert(uri, displayName)
        }
    }

    data class HomeLocalEntry(
        val displayName: String,
        val localPath: String?,
        val treeUri: String?
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        openFavoritesButton = view.findViewById(R.id.home_open_favorites)
        addAuthorizedDirButton = view.findViewById(R.id.home_add_authorized_dir)
        addNetworkButton = view.findViewById(R.id.home_add_network)
        localDirsList = view.findViewById(R.id.home_local_dirs_list)
        recentsList = view.findViewById(R.id.home_recents_list)
        networkList = view.findViewById(R.id.home_network_list)
        
        localDirsAdapter = ArrayAdapter(requireContext(), R.layout.item_home_rect, mutableListOf())
        recentsAdapter = ArrayAdapter(requireContext(), R.layout.item_home_rect, mutableListOf())
        networkAdapter = ArrayAdapter(requireContext(), R.layout.item_home_rect, mutableListOf())
        
        localDirsList.adapter = localDirsAdapter
        recentsList.adapter = recentsAdapter
        networkList.adapter = networkAdapter
        
        refreshLocalDirs()

        openFavoritesButton.setOnClickListener {
            (activity as? MainActivity)?.showFavoritesPage()
        }
        addAuthorizedDirButton.setOnClickListener {
            addAuthorizedDirLauncher.launch(null)
        }
        addNetworkButton.setOnClickListener {
            (activity as? MainActivity)?.showMorePage()
            statusMessage(getString(R.string.home_network_add_hint))
        }
        
        // Remove authorized dir via long press
        localDirsList.setOnItemLongClickListener { _, _, position, _ ->
            val entry = localDirs.getOrNull(position) ?: return@setOnItemLongClickListener false
            if (entry.treeUri != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.home_remove_authorized_dir)
                    .setMessage(entry.displayName)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        FilesRuntime.authorizedDirStore(requireContext()).remove(entry.treeUri)
                        runCatching { 
                            requireContext().contentResolver.releasePersistableUriPermission(
                                Uri.parse(entry.treeUri), 
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            ) 
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            } else {
                false
            }
        }

        localDirsList.setOnItemClickListener { _, _, position, _ ->
            val entry = localDirs.getOrNull(position) ?: return@setOnItemClickListener
            if (entry.treeUri != null) {
                (activity as? MainActivity)?.showDirectoryModeWithSafTree(entry.treeUri, fromFolders = true)
            } else if (entry.localPath != null) {
                (activity as? MainActivity)?.showDirectoryModeWithPath(entry.localPath, fromFolders = true)
            }
        }
        recentsList.setOnItemClickListener { _, _, position, _ ->
            val item = recents.getOrNull(position) ?: return@setOnItemClickListener
            (activity as? MainActivity)?.showReaderModeWithPath(item.path)
        }
        recentsList.setOnItemLongClickListener { _, _, position, _ ->
            val item = recents.getOrNull(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(requireContext())
                .setTitle(item.title)
                .setMessage(item.path)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        ReaderRuntime.historyRepository(requireContext()).deleteOne(item.path)
                        statusMessage(getString(R.string.home_recent_deleted))
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        
        networkList.setOnItemClickListener { _, _, position, _ ->
            val config = networkConfigs.getOrNull(position) ?: return@setOnItemClickListener
            (activity as? MainActivity)?.showDirectoryModeWithNetwork(config.id, fromFolders = true)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ReaderRuntime.historyRepository(requireContext()).observeRecent(limit = 30).collect { list ->
                recents.clear()
                recents.addAll(list)
                recentsAdapter.clear()
                recentsAdapter.addAll(list.map { "${it.title}  •  ${it.fileType.name}  •  ${it.path}" })
                recentsAdapter.notifyDataSetChanged()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            FilesRuntime.networkConfigRepository(requireContext()).observeAll().collect { list ->
                networkConfigs.clear()
                networkConfigs.addAll(list.filter { it.protocol == NetworkProtocol.SMB || it.protocol == NetworkProtocol.FTP })
                networkAdapter.clear()
                if (networkConfigs.isEmpty()) {
                    networkAdapter.add(getString(R.string.home_network_add_hint))
                } else {
                    networkAdapter.addAll(networkConfigs.map { "${it.protocol.name}  •  ${it.name}  •  ${it.host}:${it.port}" })
                }
                networkAdapter.notifyDataSetChanged()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            FilesRuntime.authorizedDirStore(requireContext()).observe().collect { list ->
                authorizedDirs = list
                refreshLocalDirs()
            }
        }
    }

    private fun refreshLocalDirs() {
        val downloadPath = "/storage/emulated/0/Download"
        localDirs.clear()
        localDirs.add(HomeLocalEntry(getString(R.string.home_local_downloads), downloadPath, null))
        localDirs.addAll(
            authorizedDirs.map { authorized ->
                val resolved = resolveSafTreeToLocalPath(authorized.treeUri)
                val display = if (resolved == null) authorized.displayName else "${authorized.displayName}  •  $resolved"
                HomeLocalEntry(display, resolved, authorized.treeUri)
            }
        )
        localDirsAdapter.clear()
        localDirsAdapter.addAll(localDirs.map { it.displayName })
        localDirsAdapter.notifyDataSetChanged()
    }

    private fun deriveTreeDisplayName(uri: Uri): String {
        val raw = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        return if (raw.isBlank()) uri.toString() else raw
    }

    private fun persistSafPermission(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun resolveSafTreeToLocalPath(treeUri: String): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            val split = docId.split(":", limit = 2)
            if (split.size != 2) {
                null
            } else {
                val volume = split[0]
                val relative = split[1].trim('/')
                val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
                if (relative.isBlank()) base else "$base/$relative"
            }
        }.getOrNull()
    }

    private fun statusMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
