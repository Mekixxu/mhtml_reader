package core.reader.pdf

import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.flow.Flow

interface PdfReaderController {
    suspend fun open(file: File)
    suspend fun goToPage(pageIndex: Int)
    suspend fun getPageCount(): Int
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap
    suspend fun close()
    fun observePageChanges(): Flow<Int>
}
