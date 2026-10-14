package com.html_reader.files

import android.widget.AdapterView

internal object FilesListItemResolver {
    fun entryAt(parent: AdapterView<*>, position: Int): BrowserEntry? =
        parent.getItemAtPosition(position) as? BrowserEntry
}
