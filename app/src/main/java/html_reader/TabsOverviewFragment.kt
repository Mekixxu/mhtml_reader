package com.html_reader

import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.reader.model.ReaderTab
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TabsOverviewFragment : Fragment(R.layout.fragment_tabs_overview) {
    private lateinit var switchButton: Button
    private lateinit var closeButton: Button
    private lateinit var closeAllButton: Button
    private lateinit var listView: ListView
    private lateinit var statusLabel: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val tabs = mutableListOf<ReaderTab>()
    private var selectedTabId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        switchButton = view.findViewById(R.id.tabs_switch)
        closeButton = view.findViewById(R.id.tabs_close)
        closeAllButton = view.findViewById(R.id.tabs_close_all)
        listView = view.findViewById(R.id.tabs_list)
        statusLabel = view.findViewById(R.id.tabs_status)
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        registerForContextMenu(listView)

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedTabId = tabs.getOrNull(position)?.tabId
            renderList()
        }
        switchButton.setOnClickListener {
            val tabId = selectedTabId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                ReaderRuntime.viewModel(requireContext()).switchTo(tabId)
                (activity as? MainActivity)?.showReaderMode()
            }
        }
        closeButton.setOnClickListener {
            val tabId = selectedTabId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                ReaderRuntime.viewModel(requireContext()).closeTab(tabId)
                statusLabel.text = getString(R.string.tabs_closed)
            }
        }
        closeAllButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                ReaderRuntime.viewModel(requireContext()).closeAll()
                selectedTabId = null
                statusLabel.text = getString(R.string.tabs_closed_all)
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
            menu.add(0, 1, 0, "Switch")
            menu.add(0, 2, 0, "Close")
            menu.add(0, 3, 0, "Close All")
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 3) {
            viewLifecycleOwner.lifecycleScope.launch {
                ReaderRuntime.viewModel(requireContext()).closeAll()
                selectedTabId = null
                statusLabel.text = getString(R.string.tabs_closed_all)
            }
            return true
        }

        val info = item.menuInfo as? AdapterView.AdapterContextMenuInfo ?: return super.onContextItemSelected(item)
        val tab = tabs.getOrNull(info.position) ?: return super.onContextItemSelected(item)

        when (item.itemId) {
            1 -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    ReaderRuntime.viewModel(requireContext()).switchTo(tab.tabId)
                    (activity as? MainActivity)?.showReaderMode()
                }
                return true
            }
            2 -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    ReaderRuntime.viewModel(requireContext()).closeTab(tab.tabId)
                    statusLabel.text = getString(R.string.tabs_closed)
                }
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    private fun renderList() {
        adapter.clear()
        adapter.addAll(
            tabs.map {
                val selected = if (it.tabId == selectedTabId) "▶ " else ""
                "$selected${it.title ?: it.tabId}  •  ${it.fileType.name}"
            }
        )
        adapter.notifyDataSetChanged()
    }
}
