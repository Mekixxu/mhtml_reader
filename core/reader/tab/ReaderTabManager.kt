package core.reader.tab

import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.model.ReaderTab
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

/**
 * Tab管理接口。统一打开、观察、切换、关闭tab。
 */
interface ReaderTabManager {
    fun observeTabs(): StateFlow<List<ReaderTab>>
    fun observeCurrentTabId(): StateFlow<String?>
    fun openNewTab(request: OpenRequest): Flow<OpenState>
    suspend fun closeTab(tabId: String)
    suspend fun closeAll()
    suspend fun switchTo(tabId: String)
}
