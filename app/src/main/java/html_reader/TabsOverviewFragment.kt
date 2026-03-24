package com.html_reader

import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.database.entity.enums.SourceType
import core.reader.model.ReaderTab
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TabsOverviewFragment : Fragment(R.layout.fragment_tabs_overview) {
    private lateinit var listView: ListView
    private lateinit var statusLabel: TextView
    private lateinit var adapter: TabsAdapter
    private lateinit var favoritesRepository: core.data.repo.FavoritesRepository
    private var statusDefaultColor: Int = 0
    private val tabs = mutableListOf<ReaderTab>()
    private var selectedTabId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listView = view.findViewById(R.id.tabs_list)
        statusLabel = view.findViewById(R.id.tabs_status)
        statusDefaultColor = statusLabel.currentTextColor
        adapter = TabsAdapter()
        listView.adapter = adapter
        registerForContextMenu(listView)
        favoritesRepository = FilesRuntime.favoritesRepository(requireContext())

        listView.setOnItemClickListener { _, _, position, _ ->
            val tab = tabs.getOrNull(position) ?: return@setOnItemClickListener
            selectedTabId = tab.tabId
            renderList()
            viewLifecycleOwner.lifecycleScope.launch {
                ReaderRuntime.viewModel(requireContext()).switchTo(tab.tabId)
                (activity as? MainActivity)?.showReaderMode()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ReaderRuntime.viewModel(requireContext()).tabs.collect { list ->
                tabs.clear()
                tabs.addAll(list.reversed())
                if (selectedTabId == null || tabs.none { it.tabId == selectedTabId }) {
                    selectedTabId = tabs.firstOrNull()?.tabId
                }
                renderList()
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v.id == R.id.tabs_list) {
            val info = menuInfo as AdapterView.AdapterContextMenuInfo
            val tab = tabs.getOrNull(info.position) ?: return
            menu.setHeaderTitle(tab.title ?: tab.tabId)
            menu.add(0, 3, 0, getString(R.string.tabs_close_all))
            menu.add(0, 4, 0, getString(R.string.files_action_add_favorite))
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 3) {
            viewLifecycleOwner.lifecycleScope.launch {
                ReaderRuntime.viewModel(requireContext()).closeAll()
                selectedTabId = null
                showStatus(R.string.tabs_closed_all, isSuccess = true)
            }
            return true
        }

        when (item.itemId) {
            4 -> {
                val info = item.menuInfo as? AdapterView.AdapterContextMenuInfo ?: return super.onContextItemSelected(item)
                val tab = tabs.getOrNull(info.position) ?: return super.onContextItemSelected(item)
                viewLifecycleOwner.lifecycleScope.launch {
                    val sourceType = when {
                        tab.sourcePathRaw.startsWith("smb://") -> SourceType.SMB
                        tab.sourcePathRaw.startsWith("ftp://") -> SourceType.FTP
                        else -> SourceType.LOCAL
                    }
                    favoritesRepository.addFile(
                        parentId = null,
                        name = tab.title ?: "Unknown",
                        path = tab.sourcePathRaw,
                        sourceType = sourceType
                    )
                    Toast.makeText(requireContext(), getString(R.string.favorites_added), Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    private fun renderList() {
        adapter.notifyDataSetChanged()
        val selectedIndex = tabs.indexOfFirst { it.tabId == selectedTabId }
        if (selectedIndex >= 0) {
            listView.setItemChecked(selectedIndex, true)
        } else {
            listView.clearChoices()
        }
    }

    private fun showStatus(messageRes: Int, isSuccess: Boolean = false, isError: Boolean = false) {
        statusLabel.text = getString(messageRes)
        statusLabel.setTextColor(
            when {
                isError -> ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                isSuccess -> ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                else -> statusDefaultColor
            }
        )
    }

    private fun closeTab(tab: ReaderTab) {
        viewLifecycleOwner.lifecycleScope.launch {
            ReaderRuntime.viewModel(requireContext()).closeTab(tab.tabId)
            showStatus(R.string.tabs_closed, isSuccess = true)
        }
    }

    private fun formatTabTitle(tab: ReaderTab): String {
        val selected = if (tab.tabId == selectedTabId) "▶ " else ""
        return "$selected${tab.title ?: tab.tabId}  •  ${tab.fileType.name}"
    }

    private inner class TabsAdapter : BaseAdapter() {
        override fun getCount(): Int = tabs.size

        override fun getItem(position: Int): Any = tabs[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tabs_overview_entry, parent, false)
            val tab = tabs[position]
            val titleView = rowView.findViewById<TextView>(R.id.tabs_item_title)
            val closeView = rowView.findViewById<TextView>(R.id.tabs_item_close)
            titleView.text = formatTabTitle(tab)
            closeView.setOnClickListener { closeTab(tab) }
            return rowView
        }
    }
}
