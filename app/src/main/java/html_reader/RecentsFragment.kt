package com.html_reader

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import core.data.repo.HistoryRepository
import kotlinx.coroutines.launch

class RecentsFragment : Fragment(R.layout.fragment_recents) {
    private lateinit var clearButton: Button
    private lateinit var listView: ListView
    private lateinit var emptyLabel: TextView
    private lateinit var historyRepository: HistoryRepository
    private lateinit var adapter: ArrayAdapter<String>
    private val historyItems = mutableListOf<core.database.entity.HistoryEntity>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        clearButton = view.findViewById(R.id.recents_clear_button)
        listView = view.findViewById(R.id.recents_list)
        emptyLabel = view.findViewById(R.id.recents_empty_label)
        historyRepository = ReaderRuntime.historyRepository(requireContext())

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        clearButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all history?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        historyRepository.clearAll()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = historyItems.getOrNull(position) ?: return@setOnItemClickListener
            (activity as? MainActivity)?.showReaderModeWithPath(item.path)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            historyRepository.observeRecent(500).collect { list ->
                historyItems.clear()
                historyItems.addAll(list)
                val displayList = list.map { "${it.title}  •  ${it.path}" }
                adapter.clear()
                adapter.addAll(displayList)
                adapter.notifyDataSetChanged()
                
                val isEmpty = list.isEmpty()
                emptyLabel.visibility = if (isEmpty) View.VISIBLE else View.GONE
                listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
                clearButton.isEnabled = !isEmpty
            }
        }
    }
}
