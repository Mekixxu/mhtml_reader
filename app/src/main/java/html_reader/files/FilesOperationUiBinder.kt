package com.html_reader.files

import android.view.View
import android.widget.ProgressBar

object FilesOperationUiBinder {
    fun onBeforeRun(progressBar: ProgressBar, setButtonsEnabled: (Boolean) -> Unit) {
        setButtonsEnabled(false)
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressBar.progress = 0
    }

    fun onProgress(progressBar: ProgressBar, progress: FilesOpProgress) {
        if (progress.indeterminate) {
            progressBar.isIndeterminate = true
            return
        }
        progressBar.isIndeterminate = false
        progressBar.max = progress.max
        progressBar.progress = progress.current
    }

    fun onAfterRun(progressBar: ProgressBar, setButtonsEnabled: (Boolean) -> Unit) {
        setButtonsEnabled(true)
        progressBar.visibility = View.GONE
        progressBar.isIndeterminate = false
        progressBar.progress = 0
    }
}
