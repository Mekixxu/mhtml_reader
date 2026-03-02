package core.vfs.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * 各扩展函数仅适用于SAF类型，其他类型不适用
 */
fun DocumentFile.safeFindFile(name: String): DocumentFile? =
    listFiles().firstOrNull { it.name == name }

/**
 * SAF目录排序，仅用于SAF类型
 */
fun Context.safListSorted(uri: Uri): List<DocumentFile> {
    val dir = DocumentFile.fromTreeUri(this, uri) ?: return emptyList()
    return dir.listFiles().sortedBy { it.name }
}
