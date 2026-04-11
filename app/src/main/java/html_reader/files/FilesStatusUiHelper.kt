package com.html_reader.files

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import android.widget.Toast

object FilesStatusUiHelper {
    fun bindErrorClick(context: Context, statusView: TextView, value: String) {
        statusView.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Error Details")
                .setMessage(value)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton("Copy") { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Error Message", value))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    fun clearErrorClick(statusView: TextView) {
        statusView.setOnClickListener(null)
        statusView.isClickable = false
    }
}
