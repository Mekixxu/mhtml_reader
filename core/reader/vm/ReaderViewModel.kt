package core.reader.vm

import androidx.lifecycle.ViewModel
import core.reader.tab.ReaderTabManager
import core.reader.model.OpenState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

/**
 * Reader VM，只做状态管理/流转，不含UI。
 */
class ReaderViewModel(
    private val tabManager: ReaderTabManager
) : ViewModel() {
    val tabs: StateFlow<List<core.reader.model.ReaderTab>> = tabManager.observeTabs()
    fun open(request: core.reader.model.OpenRequest): Flow<OpenState> = tabManager.openNewTab(request)
    suspend fun closeTab(tabId: String) = tabManager.closeTab(tabId)
    suspend fun closeAll() = tabManager.closeAll()
    suspend fun switchTo(tabId: String) = tabManager.switchTo(tabId)
}
