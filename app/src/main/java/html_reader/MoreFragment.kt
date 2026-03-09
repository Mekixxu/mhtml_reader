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
import core.backup.JsonBackupManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {
    private val jsonBackupManager: JsonBackupManager by lazy {
        JsonBackupManager(
            favoritesRepo = FilesRuntime.favoritesRepository(requireContext()),
            historyRepo = ReaderRuntime.historyRepository(requireContext()),
            networkRepo = FilesRuntime.networkConfigRepository(requireContext()),
            titleCacheRepo = FilesRuntime.titleCacheRepository(requireContext())
        )
    }
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
        val encodingSpinner = Spinner(requireContext())

        val protocolValues = listOf(NetworkProtocol.SMB, NetworkProtocol.FTP)
        val encodingValues = listOf("Auto", "UTF-8", "GBK", "Big5", "ISO-8859-1", "Shift_JIS", "Windows-1251")

        protocolSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            protocolValues.map { it.name }
        )
        encodingSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            encodingValues
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
        root.addView(encodingSpinner)

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
            encodingSpinner.setSelection(encodingValues.indexOf(existing.encoding).coerceAtLeast(0))
        } else {
            protocolSpinner.setSelection(0)
            portInput.setText("445")
            pathInput.setText("/")
            anonymousCheck.isChecked = false
            encodingSpinner.setSelection(0)
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
                val encoding = encodingValues[encodingSpinner.selectedItemPosition]
                
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
                    defaultPath = path,
                    encoding = encoding
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
                "$selected${it.protocol.name}  ${it.name}  •  ${it.host}:${it.port}  •  $authLabel  •  ${it.encoding}  •  ${it.defaultPath}"
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
        return jsonBackupManager.exportAll()
    }

    private suspend fun applyMetadataJson(text: String) {
        val result = jsonBackupManager.importAll(text)
        result.getOrThrow()
    }


}
