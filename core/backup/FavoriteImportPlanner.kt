package core.backup

import core.backup.model.FavoriteDto

/**
 * 一个待导入的 FOLDER 节点。
 * parentOldId 非空时，其父节点必须在本节点之前导入。
 */
data class FavoriteImportNode(
    val dto: FavoriteDto,
    val parentOldId: Long?
)

data class FavoriteImportPlan(
    val nodes: List<FavoriteImportNode>,
    /**
     * 规划过程中实际访问的节点/边次数，供测试证明线性复杂度。
     */
    val visitCount: Long
)

/**
 * FOLDER 导入顺序规划：
 * - parentId 为空或 id 为空 -> 根节点；
 * - 能通过 parentId 链从根节点到达的节点按层序导入，保证父先于子；
 * - 父 ID 缺失、循环或不可达的整个连通分量按旧兼容策略全部挂根。
 *
 * 不递归、不反复扫描 pending，复杂度 O(n+e)。
 */
class FavoriteImportPlanner {

    fun plan(folders: List<FavoriteDto>): FavoriteImportPlan {
        var visits = 0L
        val byId = LinkedHashMap<Long, FavoriteDto>()
        val rootDtos = ArrayDeque<FavoriteDto>()

        folders.forEach { dto ->
            visits += 1
            val id = dto.id
            if (id == null) {
                rootDtos.add(dto)
            } else {
                byId[id] = dto
                if (dto.parentId == null) {
                    rootDtos.add(dto)
                }
            }
        }

        val childrenByParent = HashMap<Long, MutableList<FavoriteDto>>()
        folders.forEach { dto ->
            visits += 1
            val id = dto.id ?: return@forEach
            val parentId = dto.parentId ?: return@forEach
            if (byId.containsKey(parentId)) {
                childrenByParent.getOrPut(parentId) { ArrayList() }.add(dto)
            }
        }

        val planned = ArrayList<FavoriteImportNode>(folders.size)
        val reachable = HashSet<Long>()
        val queue = ArrayDeque<FavoriteImportNode>()
        rootDtos.forEach { dto ->
            visits += 1
            queue.add(FavoriteImportNode(dto, parentOldId = null))
        }

        while (queue.isNotEmpty()) {
            visits += 1
            val node = queue.removeFirst()
            planned.add(node)
            val oldId = node.dto.id ?: continue
            reachable.add(oldId)
            childrenByParent[oldId]?.forEach { child ->
                visits += 1
                queue.add(FavoriteImportNode(child, parentOldId = oldId))
            }
        }

        // 不可达节点通常来自缺失父 ID 的断链或纯环，全部按旧行为挂根，保持输入顺序。
        folders.forEach { dto ->
            visits += 1
            val id = dto.id ?: return@forEach
            if (!reachable.contains(id)) {
                planned.add(FavoriteImportNode(dto, parentOldId = null))
                reachable.add(id)
            }
        }

        return FavoriteImportPlan(nodes = planned, visitCount = visits)
    }
}
