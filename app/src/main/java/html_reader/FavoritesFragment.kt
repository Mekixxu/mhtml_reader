package com.html_reader

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.database.entity.FavoriteEntity
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {
    private lateinit var titleLabel: TextView
    private lateinit var upButton: Button
    private lateinit var addFolderButton: Button
    private lateinit var addFileButton: Button
    private lateinit var renameButton: Button
    private lateinit var deleteButton: Button
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var observeJob: Job? = null
    private val items = mutableListOf<FavoriteEntity>()
    private val networkConfigs = mutableListOf<NetworkConfigEntity>()
    private var currentParentId: Long? = null
    private var selectedItemId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_favorites, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        titleLabel = view.findViewById(R.id.favorites_title)
        upButton = view.findViewById(R.id.favorites_up)
        addFolderButton = view.findViewById(R.id.favorites_add_folder)
        addFileButton = view.findViewById(R.id.favorites_add_file)
        renameButton = view.findViewById(R.id.favorites_rename)
        deleteButton = view.findViewById(R.id.favorites_delete)
        listView = view.findViewById(R.id.favorites_list)
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        refreshTitle()

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@setOnItemClickListener
            selectedItemId = item.id
            if (item.type == FavoriteType.FOLDER) {
                currentParentId = item.id
                selectedItemId = null
                observeCurrentFolder()
                return@setOnItemClickListener
            }
            if (item.path.isBlank()) {
                showShort(getString(R.string.favorites_unreachable))
                return@setOnItemClickListener
            }
            if (item.sourceType == SourceType.LOCAL) {
                val target = File(item.path)
                if (!target.exists() || !target.isFile) {
                    showShort(getString(R.string.favorites_unreachable))
                    return@setOnItemClickListener
                }
                (activity as? MainActivity)?.showReaderModeWithPath(item.path)
            } else if (item.sourceType == SourceType.FTP || item.sourceType == SourceType.SMB) {
                openNetworkFavorite(item)
            } else {
                showShort(getString(R.string.favorites_unreachable))
            }
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = items.getOrNull(position) ?: return@setOnItemLongClickListener true
            selectedItemId = item.id
            renderList()
            true
        }

        upButton.setOnClickListener {
            val parentId = currentParentId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val repo = FilesRuntime.favoritesRepository(requireContext())
                val parent = repo.getById(parentId)
                currentParentId = parent?.parentId
                selectedItemId = null
                observeCurrentFolder()
            }
        }
        addFolderButton.setOnClickListener {
            promptSingleInput(
                title = getString(R.string.favorites_add_folder),
                hint = getString(R.string.favorites_input_folder_name),
                initial = ""
            ) { folderName ->
                viewLifecycleOwner.lifecycleScope.launch {
                    FilesRuntime.favoritesRepository(requireContext()).addFolder(currentParentId, folderName)
                    showShort(getString(R.string.favorites_added))
                }
            }
        }
        addFileButton.setOnClickListener {
            promptAddFileFavorite()
        }
        renameButton.setOnClickListener {
            val selected = selectedItem() ?: return@setOnClickListener
            promptSingleInput(
                title = getString(R.string.favorites_rename),
                hint = if (selected.type == FavoriteType.FOLDER) getString(R.string.favorites_input_folder_name) else getString(R.string.favorites_input_file_name),
                initial = selected.name
            ) { newName ->
                viewLifecycleOwner.lifecycleScope.launch {
                    FilesRuntime.favoritesRepository(requireContext()).rename(selected.id, newName)
                    showShort(getString(R.string.favorites_updated))
                }
            }
        }
        deleteButton.setOnClickListener {
            val selected = selectedItem() ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(selected.name)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        FilesRuntime.favoritesRepository(requireContext()).deleteSubtree(selected.id)
                        selectedItemId = null
                        showShort(getString(R.string.favorites_removed))
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        observeCurrentFolder()
    }

    private fun observeCurrentFolder() {
        observeJob?.cancel()
        refreshTitle()
        observeJob = viewLifecycleOwner.lifecycleScope.launch {
            FilesRuntime.favoritesRepository(requireContext()).observeChildren(parentId = currentParentId).collect { list ->
                items.clear()
                items.addAll(list)
                if (selectedItemId != null && items.none { it.id == selectedItemId }) {
                    selectedItemId = null
                }
                renderList()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            FilesRuntime.networkConfigRepository(requireContext()).observeAll().collect { list ->
                networkConfigs.clear()
                networkConfigs.addAll(list)
                renderList()
            }
        }
    }

    private fun renderList() {
        adapter.clear()
        adapter.addAll(
            items.map {
                val selectedPrefix = if (it.id == selectedItemId) "▶ " else ""
                val typeLabel = if (it.type == FavoriteType.FOLDER) "DIR" else "FILE"
                val pathLabel = if (it.type == FavoriteType.FOLDER) "" else "  •  ${it.path}"
                val invalidLabel = if (it.type == FavoriteType.FILE && !isFavoriteReachable(it)) {
                    "  •  ${getString(R.string.favorites_invalid)}"
                } else {
                    ""
                }
                "$selectedPrefix$typeLabel  ${it.name}$pathLabel$invalidLabel"
            }
        )
        adapter.notifyDataSetChanged()
    }

    private fun selectedItem(): FavoriteEntity? {
        val selected = items.firstOrNull { it.id == selectedItemId }
        if (selected == null) {
            showShort(getString(R.string.favorites_select_item_first))
        }
        return selected
    }

    private fun promptSingleInput(title: String, hint: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            setText(initial)
            setSelection(text.length)
            this.hint = hint
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) {
                    onOk(value)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptAddFileFavorite() {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = getString(R.string.favorites_input_file_name)
        }
        val pathInput = EditText(requireContext()).apply {
            hint = getString(R.string.favorites_input_file_path)
        }
        root.addView(nameInput)
        root.addView(pathInput)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.favorites_add_file)
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val path = pathInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank() || path.isBlank()) {
                    return@setPositiveButton
                }
                val sourceType = inferSourceType(path)
                viewLifecycleOwner.lifecycleScope.launch {
                    FilesRuntime.favoritesRepository(requireContext()).addFile(
                        parentId = currentParentId,
                        name = name,
                        path = path,
                        sourceType = sourceType
                    )
                    showShort(getString(R.string.favorites_added))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun inferSourceType(path: String): SourceType {
        val lower = path.lowercase()
        return when {
            lower.startsWith("ftp://") -> SourceType.FTP
            lower.startsWith("smb://") -> SourceType.SMB
            lower.startsWith("http://") || lower.startsWith("https://") -> SourceType.WEB
            else -> SourceType.LOCAL
        }
    }

    private fun refreshTitle() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = FilesRuntime.favoritesRepository(requireContext())
            val names = mutableListOf<String>()
            var cursor = currentParentId
            while (cursor != null) {
                val entity = repo.getById(cursor) ?: break
                names.add(entity.name)
                cursor = entity.parentId
            }
            val pathPart = if (names.isEmpty()) {
                "/"
            } else {
                "/" + names.asReversed().joinToString("/")
            }
            titleLabel.text = "${getString(R.string.favorites_title)}  $pathPart"
        }
    }

    private fun showShort(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun openNetworkFavorite(item: FavoriteEntity) {
        val uri = runCatching { Uri.parse(item.path) }.getOrNull() ?: run {
            showShort(getString(R.string.favorites_unreachable))
            return
        }
        val sourceType = item.sourceType
        val protocol = when (sourceType) {
            SourceType.FTP -> NetworkProtocol.FTP
            SourceType.SMB -> NetworkProtocol.SMB
            else -> null
        } ?: run {
            showShort(getString(R.string.favorites_unreachable))
            return
        }
        val host = uri.host.orEmpty()
        if (host.isBlank()) {
            showShort(getString(R.string.favorites_unreachable))
            return
        }
        val port = if (uri.port > 0) uri.port else if (protocol == NetworkProtocol.FTP) 21 else 445
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = FilesRuntime.networkConfigRepository(requireContext())
            val all = repository.getAll()
            val config = all.firstOrNull { it.protocol == protocol && it.host.equals(host, ignoreCase = true) && it.port == port }
                ?: createAdHocNetworkConfig(uri, protocol, host, port).let { temp ->
                    val id = repository.add(temp)
                    repository.getById(id)
                }
            if (config == null) {
                showShort(getString(R.string.favorites_unreachable))
                return@launch
            }
            val rawPath = URLDecoder.decode(uri.encodedPath.orEmpty().ifBlank { "/" }, "UTF-8")
            val normalized = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            val openPath = if (item.type == FavoriteType.FILE) {
                val index = normalized.lastIndexOf('/')
                if (index <= 0) "/" else normalized.substring(0, index)
            } else {
                normalized
            }
            (activity as? MainActivity)?.showDirectoryModeWithNetworkPath(config.id, openPath)
        }
    }

    private fun createAdHocNetworkConfig(
        uri: Uri,
        protocol: NetworkProtocol,
        host: String,
        port: Int
    ): NetworkConfigEntity {
        val userInfo = uri.userInfo.orEmpty()
        val username = userInfo.substringBefore(':', "").let { URLDecoder.decode(it, "UTF-8") }
        val password = userInfo.substringAfter(':', "").let { URLDecoder.decode(it, "UTF-8") }
        val defaultPath = URLDecoder.decode(uri.encodedPath.orEmpty().ifBlank { "/" }, "UTF-8")
        return NetworkConfigEntity(
            id = 0L,
            name = "Fav ${protocol.name} ${host}:${port}",
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password,
            defaultPath = if (defaultPath.startsWith("/")) defaultPath else "/$defaultPath"
        )
    }

    private fun isFavoriteReachable(item: FavoriteEntity): Boolean {
        return when (item.sourceType) {
            SourceType.LOCAL -> File(item.path).exists()
            SourceType.FTP, SourceType.SMB -> hasMatchingNetworkConfig(item)
            else -> false
        }
    }

    private fun hasMatchingNetworkConfig(item: FavoriteEntity): Boolean {
        val uri = runCatching { Uri.parse(item.path) }.getOrNull() ?: return false
        val protocol = when (item.sourceType) {
            SourceType.FTP -> NetworkProtocol.FTP
            SourceType.SMB -> NetworkProtocol.SMB
            else -> return false
        }
        val host = uri.host.orEmpty()
        if (host.isBlank()) {
            return false
        }
        val port = if (uri.port > 0) uri.port else if (protocol == NetworkProtocol.FTP) 21 else 445
        return networkConfigs.any { it.protocol == protocol && it.host.equals(host, ignoreCase = true) && it.port == port }
    }
}
