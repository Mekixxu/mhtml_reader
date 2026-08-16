package com.html_reader.files

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import android.widget.Toast
import com.html_reader.R

object FilesStatusUiHelper {
    fun bindErrorClick(context: Context, statusView: TextView, value: String) {
        statusView.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(R.string.common_error_details_title)
                .setMessage(value)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.common_copy) { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.common_error_details_title), value))
                    Toast.makeText(context, R.string.common_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    fun clearErrorClick(statusView: TextView) {
        statusView.setOnClickListener(null)
        statusView.isClickable = false
    }
}
