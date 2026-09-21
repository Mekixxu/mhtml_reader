package com.html_reader.files

import android.app.AlertDialog
import android.content.Context

object FilesPermissionHelper {
    fun showAllFilesPermissionDialog(context: Context, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(com.html_reader.R.string.permission_required_title)
            .setMessage(com.html_reader.R.string.permission_required_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
