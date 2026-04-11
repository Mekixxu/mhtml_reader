package com.html_reader.files

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import com.html_reader.R

data class FilesUiViews(
    val queryInput: EditText,
    val sortSpinner: Spinner,
    val fontSizeSpinner: Spinner,
    val currentDirLabel: TextView,
    val operationStatusLabel: TextView,
    val operationProgress: ProgressBar,
    val actionUpButton: Button,
    val actionCreateButton: Button,
    val listView: ListView
)

object FilesUiBinder {
    fun bind(root: View): FilesUiViews {
        return FilesUiViews(
            queryInput = root.findViewById(R.id.files_query_input),
            sortSpinner = root.findViewById(R.id.files_sort_spinner),
            fontSizeSpinner = root.findViewById(R.id.files_font_size_spinner),
            currentDirLabel = root.findViewById(R.id.files_current_dir),
            operationStatusLabel = root.findViewById(R.id.files_operation_status),
            operationProgress = root.findViewById(R.id.files_operation_progress),
            actionUpButton = root.findViewById(R.id.files_action_up),
            actionCreateButton = root.findViewById(R.id.files_action_create),
            listView = root.findViewById(R.id.files_list)
        )
    }
}
