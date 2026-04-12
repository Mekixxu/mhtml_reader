package com.html_reader.files

import android.app.AlertDialog
import android.content.Context

object FilesPermissionHelper {
    fun showAllFilesPermissionDialog(context: Context, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Permission Required")
            .setMessage("This app needs access to all files to function properly. Please grant the permission.")
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
