package com.html_reader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.html_reader.files.FilesPermissionHelper

internal fun FilesFragment.ensureStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !checkStoragePermission()) {
            requestStoragePermission()
        }
    }

internal fun FilesFragment.requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FilesPermissionHelper.showAllFilesPermissionDialog(requireContext()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
        }
    }


