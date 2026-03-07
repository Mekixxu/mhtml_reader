package com.html_reader

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.session.entity.FolderSessionEntity
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FoldersOverviewFragment : Fragment(R.layout.fragment_folders_overview) {
    private lateinit var createButton: Button
    private lateinit var openButton: Button
    private lateinit var deleteButton: Button
    private lateinit var listView: ListView
    private lateinit var statusLabel: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val sessions = mutableListOf<FolderSessionEntity>()
    private var selectedId: Long? = null

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        createButton = view.findViewById(R.id.folders_create)
        openButton = view.findViewById(R.id.folders_open)
        deleteButton = view.findViewById(R.id.folders_delete)
        listView = view.findViewById(R.id.folders_list)
        statusLabel = view.findViewById(R.id.folders_status)
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        createButton.setOnClickListener { promptCreateSession() }
        openButton.setOnClickListener {
            val id = selectedId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                FilesRuntime.currentSessionStore(requireContext()).set(id)
                (activity as? MainActivity)?.showDirectoryMode()
            }
        }
        deleteButton.setOnClickListener {
            val id = selectedId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                FilesRuntime.folderSessionRepository(requireContext()).delete(id)
                FilesRuntime.sessionSourceStore(requireContext()).removeSession(id)
                selectedId = null
                statusLabel.text = getString(R.string.folders_deleted)
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            selectedId = sessions.getOrNull(position)?.id
            renderList()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            FilesRuntime.folderSessionRepository(requireContext()).observeAll().collect { list ->
                sessions.clear()
                sessions.addAll(list)
                if (selectedId == null || sessions.none { it.id == selectedId }) {
                    selectedId = sessions.firstOrNull()?.id
                }
                renderList()
            }
        }
    }

    private fun promptCreateSession() {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.folders_create_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input.text?.toString()?.trim().orEmpty()
                val name = if (raw.isBlank()) getString(R.string.folders_default_name) else raw
                val root = requireContext().filesDir.parentFile ?: requireContext().filesDir
                viewLifecycleOwner.lifecycleScope.launch {
                    val createdId = FilesRuntime.folderSessionRepository(requireContext())
                        .add(name, root.absolutePath)
                    selectedId = createdId
                    FilesRuntime.currentSessionStore(requireContext()).set(createdId)
                    statusLabel.text = getString(R.string.folders_created)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderList() {
        adapter.clear()
        adapter.addAll(
            sessions.map {
                val selected = if (it.id == selectedId) "▶ " else ""
                "$selected${it.name}  •  ${File(it.currentPath).absolutePath}"
            }
        )
        adapter.notifyDataSetChanged()
    }
}
