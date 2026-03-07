package com.html_reader

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.database.entity.FavoriteEntity
import core.database.entity.HistoryEntity
import core.database.entity.NetworkConfigEntity
import core.database.entity.TitleCacheEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.FileType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {
    private lateinit var addButton: Button
    private lateinit var editButton: Button
    private lateinit var deleteButton: Button
    private lateinit var openFavoritesButton: Button
    private lateinit var openSettingsButton: Button
    private lateinit var exportMetaButton: Button
    private lateinit var importMetaButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val configs = mutableListOf<NetworkConfigEntity>()
    private var selectedId: Long? = null
    private val settingsPrefsName = "app_settings"
    private val themeModeKey = "theme_mode"
    private val backupSchemaVersion = 1
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            exportToUri(uri)
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_more, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        addButton = view.findViewById(R.id.more_network_add)
        editButton = view.findViewById(R.id.more_network_edit)
        deleteButton = view.findViewById(R.id.more_network_delete)
        openFavoritesButton = view.findViewById(R.id.more_open_favorites)
        openSettingsButton = view.findViewById(R.id.more_open_settings)
        exportMetaButton = view.findViewById(R.id.more_export_meta)
        importMetaButton = view.findViewById(R.id.more_import_meta)
        statusLabel = view.findViewById(R.id.more_network_status)
        listView = view.findViewById(R.id.more_network_list)
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        statusLabel.text = getString(R.string.more_network_ready)

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedId = configs.getOrNull(position)?.id
            renderList()
        }
        addButton.setOnClickListener { showEditDialog(null) }
        editButton.setOnClickListener {
            val selected = configs.firstOrNull { it.id == selectedId } ?: return@setOnClickListener
            showEditDialog(selected)
        }
        deleteButton.setOnClickListener {
            val id = selectedId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                FilesRuntime.networkConfigRepository(requireContext()).delete(id)
                selectedId = null
                statusLabel.text = getString(R.string.more_network_deleted)
            }
        }
        openFavoritesButton.setOnClickListener {
            (activity as? MainActivity)?.showFavoritesPage()
        }
        openSettingsButton.setOnClickListener {
            showSettingsDialog()
        }
        exportMetaButton.setOnClickListener {
            val fileName = "metadata_export_${System.currentTimeMillis()}.json"
            exportLauncher.launch(fileName)
        }
        importMetaButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            FilesRuntime.networkConfigRepository(requireContext()).observeAll().collect { list ->
                configs.clear()
                configs.addAll(list.sortedBy { it.name.lowercase() })
                if (selectedId == null || configs.none { it.id == selectedId }) {
                    selectedId = configs.firstOrNull()?.id
                }
                renderList()
            }
        }
    }

    private fun showEditDialog(existing: NetworkConfigEntity?) {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val protocolSpinner = Spinner(requireContext())
        val nameInput = EditText(requireContext())
        val hostInput = EditText(requireContext())
        val portInput = EditText(requireContext())
        val userInput = EditText(requireContext())
        val passwordInput = EditText(requireContext())
        val pathInput = EditText(requireContext())
        val anonymousCheck = CheckBox(requireContext())
        val protocolValues = listOf(NetworkProtocol.SMB, NetworkProtocol.FTP)
        protocolSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            protocolValues.map { it.name }
        )
        nameInput.hint = getString(R.string.more_network_name_hint)
        hostInput.hint = getString(R.string.more_network_host_hint)
        portInput.hint = getString(R.string.more_network_port_hint)
        userInput.hint = getString(R.string.more_network_user_hint)
        passwordInput.hint = getString(R.string.more_network_password_hint)
        pathInput.hint = getString(R.string.more_network_path_hint)
        anonymousCheck.text = getString(R.string.more_network_ftp_anonymous)
        root.addView(protocolSpinner)
        root.addView(nameInput)
        root.addView(hostInput)
        root.addView(portInput)
        root.addView(anonymousCheck)
        root.addView(userInput)
        root.addView(passwordInput)
        root.addView(pathInput)

        fun currentProtocol(): NetworkProtocol = protocolValues[protocolSpinner.selectedItemPosition]
        fun applyAuthUiState(protocol: NetworkProtocol, anonymous: Boolean) {
            val ftpMode = protocol == NetworkProtocol.FTP
            anonymousCheck.visibility = if (ftpMode) View.VISIBLE else View.GONE
            val disableCredentialInput = ftpMode && anonymous
            userInput.isEnabled = !disableCredentialInput
            passwordInput.isEnabled = !disableCredentialInput
            if (disableCredentialInput) {
                userInput.setText("")
                passwordInput.setText("")
            }
        }

        if (existing != null) {
            protocolSpinner.setSelection(protocolValues.indexOf(existing.protocol).coerceAtLeast(0))
            nameInput.setText(existing.name)
            hostInput.setText(existing.host)
            portInput.setText(existing.port.toString())
            userInput.setText(existing.username)
            passwordInput.setText(existing.password)
            pathInput.setText(existing.defaultPath)
            anonymousCheck.isChecked = existing.protocol == NetworkProtocol.FTP && existing.username.isBlank()
        } else {
            protocolSpinner.setSelection(0)
            portInput.setText("445")
            pathInput.setText("/")
            anonymousCheck.isChecked = false
        }
        applyAuthUiState(currentProtocol(), anonymousCheck.isChecked)

        protocolSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val protocol = currentProtocol()
                val currentPort = portInput.text?.toString()?.trim().orEmpty()
                if (protocol == NetworkProtocol.FTP && (currentPort.isBlank() || currentPort == "445")) {
                    portInput.setText("21")
                }
                if (protocol == NetworkProtocol.SMB && (currentPort.isBlank() || currentPort == "21")) {
                    portInput.setText("445")
                }
                applyAuthUiState(protocol, anonymousCheck.isChecked)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        anonymousCheck.setOnCheckedChangeListener { _, isChecked ->
            applyAuthUiState(currentProtocol(), isChecked)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.more_network_add_title else R.string.more_network_edit_title)
            .setView(root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val protocol = protocolValues[protocolSpinner.selectedItemPosition]
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val host = hostInput.text?.toString()?.trim().orEmpty()
                val port = portInput.text?.toString()?.trim().orEmpty().toIntOrNull()
                val useAnonymous = protocol == NetworkProtocol.FTP && anonymousCheck.isChecked
                val username = if (useAnonymous) "" else userInput.text?.toString()?.trim().orEmpty()
                val password = if (useAnonymous) "" else passwordInput.text?.toString().orEmpty()
                val path = pathInput.text?.toString()?.trim().orEmpty().ifBlank { "/" }
                if (name.isBlank() || host.isBlank() || port == null || port <= 0) {
                    statusLabel.text = getString(R.string.more_network_invalid)
                    return@setPositiveButton
                }
                val entity = NetworkConfigEntity(
                    id = existing?.id ?: 0L,
                    name = name,
                    protocol = protocol,
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    defaultPath = path
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    if (existing == null) {
                        val newId = FilesRuntime.networkConfigRepository(requireContext()).add(entity)
                        selectedId = newId
                        statusLabel.text = getString(R.string.more_network_added)
                    } else {
                        FilesRuntime.networkConfigRepository(requireContext()).update(entity)
                        selectedId = existing.id
                        statusLabel.text = getString(R.string.more_network_updated)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderList() {
        adapter.clear()
        adapter.addAll(
            configs.map {
                val selected = if (it.id == selectedId) "▶ " else ""
                val authLabel = if (it.protocol == NetworkProtocol.FTP && it.username.isBlank()) {
                    getString(R.string.more_network_auth_anonymous)
                } else if (it.username.isBlank()) {
                    "-"
                } else {
                    it.username
                }
                "$selected${it.protocol.name}  ${it.name}  •  ${it.host}:${it.port}  •  $authLabel  •  ${it.defaultPath}"
            }
        )
        adapter.notifyDataSetChanged()
    }

    private fun showSettingsDialog() {
        val prefs = requireContext().getSharedPreferences(settingsPrefsName, android.content.Context.MODE_PRIVATE)
        val currentMode = prefs.getInt(themeModeKey, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val options = arrayOf(
            getString(R.string.more_theme_system),
            getString(R.string.more_theme_light),
            getString(R.string.more_theme_dark)
        )
        val modeValues = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val checkedIndex = modeValues.indexOf(currentMode).takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.more_settings_theme_title)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val newMode = modeValues[which]
                prefs.edit().putInt(themeModeKey, newMode).apply()
                AppCompatDelegate.setDefaultNightMode(newMode)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportToUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val jsonText = buildMetadataJson()
                requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(jsonText)
                } ?: throw IllegalStateException("open output stream failed")
                statusLabel.text = getString(R.string.more_export_success)
            }.onFailure {
                statusLabel.text = getString(R.string.more_export_failed)
            }
        }
    }

    private fun importFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val text = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                } ?: throw IllegalStateException("open input stream failed")
                applyMetadataJson(text)
                statusLabel.text = getString(R.string.more_import_success)
            }.onFailure {
                statusLabel.text = getString(R.string.more_import_failed)
            }
        }
    }

    private suspend fun buildMetadataJson(): String {
        val favorites = FilesRuntime.favoritesRepository(requireContext()).getAll()
        val history = ReaderRuntime.historyRepository(requireContext()).getAll()
        val networks = FilesRuntime.networkConfigRepository(requireContext()).getAll()
        val titleCache = FilesRuntime.titleCacheRepository(requireContext()).getAll()
        val root = JSONObject()
        root.put("schemaVersion", backupSchemaVersion)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("favorites", JSONArray().apply {
            favorites.forEach { addFavoriteJson(it) }
        })
        root.put("history", JSONArray().apply {
            history.forEach { addHistoryJson(it) }
        })
        root.put("networkConfigs", JSONArray().apply {
            networks.forEach { addNetworkJson(it) }
        })
        root.put("titleCache", JSONArray().apply {
            titleCache.forEach { addTitleCacheJson(it) }
        })
        return root.toString(2)
    }

    private suspend fun applyMetadataJson(text: String) {
        val root = JSONObject(text)
        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion != backupSchemaVersion) {
            throw IllegalArgumentException(getString(R.string.more_import_schema_mismatch))
        }
        val favoritesRepo = FilesRuntime.favoritesRepository(requireContext())
        val historyRepo = ReaderRuntime.historyRepository(requireContext())
        val networkRepo = FilesRuntime.networkConfigRepository(requireContext())
        val titleRepo = FilesRuntime.titleCacheRepository(requireContext())
        favoritesRepo.clearAll()
        historyRepo.clearAll()
        networkRepo.clearAll()
        titleRepo.clearAll()

        val favoriteArray = root.optJSONArray("favorites") ?: JSONArray()
        val folderPending = mutableListOf<JSONObject>()
        val filePending = mutableListOf<JSONObject>()
        for (i in 0 until favoriteArray.length()) {
            val item = favoriteArray.optJSONObject(i) ?: continue
            val type = parseFavoriteType(item.optString("type"))
            if (type == FavoriteType.FOLDER) folderPending.add(item) else filePending.add(item)
        }
        val idMap = mutableMapOf<Long, Long>()
        var progress = true
        while (folderPending.isNotEmpty() && progress) {
            progress = false
            val iter = folderPending.iterator()
            while (iter.hasNext()) {
                val folder = iter.next()
                val oldId = folder.optLong("id", -1L).takeIf { it > 0L }
                val oldParentId = folder.optLong("parentId", -1L).takeIf { it > 0L }
                val newParentId = oldParentId?.let { idMap[it] }
                if (oldParentId != null && newParentId == null) {
                    continue
                }
                val newId = favoritesRepo.addFolder(
                    parentId = newParentId,
                    name = folder.optString("name").ifBlank { "Folder" }
                )
                if (oldId != null) {
                    idMap[oldId] = newId
                }
                iter.remove()
                progress = true
            }
        }
        folderPending.forEach { folder ->
            val oldId = folder.optLong("id", -1L).takeIf { it > 0L }
            val newId = favoritesRepo.addFolder(parentId = null, name = folder.optString("name").ifBlank { "Folder" })
            if (oldId != null) {
                idMap[oldId] = newId
            }
        }
        filePending.forEach { file ->
            val oldParentId = file.optLong("parentId", -1L).takeIf { it > 0L }
            val newParentId = oldParentId?.let { idMap[it] }
            favoritesRepo.addFile(
                parentId = newParentId,
                name = file.optString("name").ifBlank { "File" },
                path = file.optString("path"),
                sourceType = parseSourceType(file.optString("sourceType"))
            )
        }

        val historyArray = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until historyArray.length()) {
            val item = historyArray.optJSONObject(i) ?: continue
            historyRepo.upsert(
                HistoryEntity(
                    path = item.optString("path"),
                    title = item.optString("title"),
                    lastAccess = item.optLong("lastAccess"),
                    progress = item.optDouble("progress", 0.0).toFloat(),
                    pageIndex = item.optInt("pageIndex", -1),
                    fileType = parseFileType(item.optString("fileType"))
                )
            )
        }

        val networkArray = root.optJSONArray("networkConfigs") ?: JSONArray()
        for (i in 0 until networkArray.length()) {
            val item = networkArray.optJSONObject(i) ?: continue
            networkRepo.add(
                NetworkConfigEntity(
                    id = 0L,
                    name = item.optString("name").ifBlank { "Network" },
                    protocol = parseNetworkProtocol(item.optString("protocol")),
                    host = item.optString("host"),
                    port = item.optInt("port", 21),
                    username = item.optString("username"),
                    password = item.optString("password"),
                    defaultPath = item.optString("defaultPath").ifBlank { "/" }
                )
            )
        }

        val titleArray = root.optJSONArray("titleCache") ?: JSONArray()
        for (i in 0 until titleArray.length()) {
            val item = titleArray.optJSONObject(i) ?: continue
            titleRepo.upsert(
                TitleCacheEntity(
                    path = item.optString("path"),
                    title = item.optString("title"),
                    lastModified = item.optLong("lastModified"),
                    updatedAt = item.optLong("updatedAt")
                )
            )
        }
    }

    private fun JSONArray.addFavoriteJson(entity: FavoriteEntity) {
        put(
            JSONObject()
                .put("id", entity.id)
                .put("parentId", entity.parentId)
                .put("name", entity.name)
                .put("type", entity.type.name)
                .put("path", entity.path)
                .put("sourceType", entity.sourceType.name)
                .put("createdAt", entity.createdAt)
        )
    }

    private fun JSONArray.addHistoryJson(entity: HistoryEntity) {
        put(
            JSONObject()
                .put("path", entity.path)
                .put("title", entity.title)
                .put("lastAccess", entity.lastAccess)
                .put("progress", entity.progress)
                .put("pageIndex", entity.pageIndex)
                .put("fileType", entity.fileType.name)
        )
    }

    private fun JSONArray.addNetworkJson(entity: NetworkConfigEntity) {
        put(
            JSONObject()
                .put("id", entity.id)
                .put("name", entity.name)
                .put("protocol", entity.protocol.name)
                .put("host", entity.host)
                .put("port", entity.port)
                .put("username", entity.username)
                .put("password", entity.password)
                .put("defaultPath", entity.defaultPath)
        )
    }

    private fun JSONArray.addTitleCacheJson(entity: TitleCacheEntity) {
        put(
            JSONObject()
                .put("path", entity.path)
                .put("title", entity.title)
                .put("lastModified", entity.lastModified)
                .put("updatedAt", entity.updatedAt)
        )
    }

    private fun parseFavoriteType(value: String): FavoriteType =
        runCatching { FavoriteType.valueOf(value) }.getOrDefault(FavoriteType.FILE)

    private fun parseSourceType(value: String): SourceType =
        runCatching { SourceType.valueOf(value) }.getOrDefault(SourceType.UNKNOWN)

    private fun parseFileType(value: String): FileType =
        runCatching { FileType.valueOf(value) }.getOrDefault(FileType.MHTML)

    private fun parseNetworkProtocol(value: String): NetworkProtocol =
        runCatching { NetworkProtocol.valueOf(value) }.getOrDefault(NetworkProtocol.FTP)
}
