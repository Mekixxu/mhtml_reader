package com.html_reader

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.html_reader.backup.BackupStreamIo
import com.html_reader.backup.BackupTooLargeException
import com.html_reader.files.NetworkDisplayHelper
import core.database.entity.FavoriteEntity
import core.database.entity.HistoryEntity
import core.database.entity.NetworkConfigEntity
import core.database.entity.TitleCacheEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.FileType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import java.io.IOException
import androidx.room.withTransaction
import core.backup.BackupTransactionRunner
import core.backup.JsonBackupManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoreFragment : Fragment() {
    private val jsonBackupManager: JsonBackupManager by lazy {
        JsonBackupManager(
            favoritesRepo = FilesRuntime.favoritesRepository(requireContext()),
            historyRepo = ReaderRuntime.historyRepository(requireContext()),
            networkRepo = FilesRuntime.networkConfigRepository(requireContext()),
            titleCacheRepo = FilesRuntime.titleCacheRepository(requireContext()),
            transactionRunner = object : BackupTransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T =
                    CoreRuntime.database.withTransaction { block() }
            }
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
    private var statusDefaultColor: Int = 0
    private val configs = mutableListOf<NetworkConfigEntity>()
    private var selectedId: Long? = null
    private var backupOperationInProgress = false
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
        statusDefaultColor = statusLabel.currentTextColor
        listView = view.findViewById(R.id.more_network_list)
        adapter = ArrayAdapter(requireContext(), R.layout.item_home_rect, mutableListOf())
        listView.adapter = adapter
        showStatus(R.string.more_network_ready)

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
                showStatus(R.string.more_network_deleted, isSuccess = true)
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
        val passwordInput = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pathInput = EditText(requireContext())
        val anonymousCheck = CheckBox(requireContext())
        val encodingSpinner = Spinner(requireContext())

        val decryptFailed = existing?.decryptFailed == true
        val decryptWarning = if (decryptFailed) {
            TextView(requireContext()).apply {
                text = getString(R.string.more_network_password_undecryptable)
                setTextColor(android.graphics.Color.RED)
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
        } else {
            null
        }

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
        decryptWarning?.let { root.addView(it) }
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
            if (!decryptFailed) {
                passwordInput.setText(existing.password)
            }
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
                    showStatus(R.string.more_network_invalid, isError = true)
                    return@setPositiveButton
                }
                if (decryptFailed && password.isBlank() && !useAnonymous) {
                    showStatus(R.string.more_network_password_undecryptable, isError = true)
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
                    try {
                        if (existing == null) {
                            val newId = FilesRuntime.networkConfigRepository(requireContext()).add(entity)
                            selectedId = newId
                            showStatus(R.string.more_network_added, isSuccess = true)
                        } else {
                            FilesRuntime.networkConfigRepository(requireContext()).update(entity)
                            selectedId = existing.id
                            showStatus(R.string.more_network_updated, isSuccess = true)
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        showStatus(R.string.more_network_duplicate, isError = true)
                    } catch (t: Throwable) {
                        // 真实故障（Keystore/磁盘/DB）不能误诊为重复配置
                        android.util.Log.w("MoreFragment", "network_config_save_failed err=${t.javaClass.simpleName}")
                        showStatus(R.string.common_unknown_error, isError = true)
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
                val meaningful = NetworkDisplayHelper.meaningfulName(it.name, it.protocol)
                val cleanPath = NetworkDisplayHelper.trimProtocolPrefix(it.defaultPath, it.protocol)
                val nameSegment = meaningful?.let { "$it  •  " } ?: ""
                "$selected${it.protocol.name}  •  $nameSegment${it.host}:${it.port}  •  $authLabel  •  ${it.encoding}  •  $cleanPath"
            }
        )
        adapter.notifyDataSetChanged()
        val selectedIndex = configs.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0) {
            listView.setItemChecked(selectedIndex, true)
        } else {
            listView.clearChoices()
        }
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
        if (!beginBackupOperation()) return
        val appContext = requireContext().applicationContext
        val manager = jsonBackupManager
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (view != null) showStatus(R.string.more_backup_working)
                val jsonText = manager.exportAll()
                withContext(Dispatchers.IO) {
                    val output = appContext.contentResolver.openOutputStream(uri)
                        ?: throw IOException("open output stream failed")
                    BackupStreamIo.writeUtf8(output, jsonText)
                }
                if (view != null) showStatus(R.string.more_export_success, isSuccess = true)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (view != null) showStatus(R.string.more_export_failed, isError = true)
            } finally {
                endBackupOperation()
            }
        }
    }

    private fun importFromUri(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.more_import_confirm_title)
            .setMessage(R.string.more_import_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> performImport(uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performImport(uri: Uri) {
        if (!beginBackupOperation()) return
        val appContext = requireContext().applicationContext
        val manager = jsonBackupManager
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (view != null) showStatus(R.string.more_backup_working)
                val text = withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw IOException("open input stream failed")
                    BackupStreamIo.readUtf8(input, maxBackupBytes)
                }
                manager.importAll(text).getOrThrow()
                if (view != null) showStatus(R.string.more_import_success, isSuccess = true)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (view != null) {
                    val messageRes = if (t is BackupTooLargeException) {
                        R.string.more_backup_too_large
                    } else {
                        R.string.more_import_failed
                    }
                    showStatus(messageRes, isError = true)
                }
            } finally {
                endBackupOperation()
            }
        }
    }

    private fun beginBackupOperation(): Boolean {
        if (backupOperationInProgress) {
            showStatus(R.string.more_backup_busy, isError = true)
            return false
        }
        backupOperationInProgress = true
        return true
    }

    private fun endBackupOperation() {
        backupOperationInProgress = false
    }

    internal companion object {
        /**
         * 当前生产默认不设上限，避免在用户确认前排除合法旧备份；
         * 提案值为 [BackupStreamIo.PROPOSED_MAX_BYTES]（20 MiB），确认后修改此处即可启用。
         */
        internal var maxBackupBytes: Long = Long.MAX_VALUE
    }

    private fun showStatus(messageRes: Int, isSuccess: Boolean = false, isError: Boolean = false) {
        statusLabel.text = getString(messageRes)
        statusLabel.setTextColor(
            when {
                isError -> ContextCompat.getColor(requireContext(), R.color.colorError)
                isSuccess -> ContextCompat.getColor(requireContext(), R.color.colorSuccess)
                else -> statusDefaultColor
            }
        )
    }


}
