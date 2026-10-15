package com.html_reader

import core.backup.FavoriteImportPlanner
import core.backup.model.FavoriteDto
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteImportPlannerTest {

    @Test
    fun reverseDeepChain_plansLinearlyAndParentBeforeChild() = runBlocking {
        val count = 100_000
        val folders = (count downTo 1).map { id ->
            folder(
                id = id.toLong(),
                parentId = if (id == 1) null else (id - 1).toLong()
            )
        }

        val plan = FavoriteImportPlanner().plan(folders)

        assertEquals(count, plan.nodes.size)
        assertTrue(
            "逆序深链访问次数必须保持线性，实际 ${plan.visitCount}",
            plan.visitCount <= 6L * count + 10
        )

        val indexById = HashMap<Long, Int>()
        plan.nodes.forEachIndexed { index, node ->
            node.dto.id?.let { indexById[it] = index }
        }
        plan.nodes.forEach { node ->
            val parent = node.parentOldId ?: return@forEach
            val child = node.dto.id
            assertNotNull(parent)
            assertNotNull(child)
            assertTrue(
                "父节点必须先于子节点导入",
                indexById.getValue(parent) < indexById.getValue(child!!)
            )
        }
    }

    @Test
    fun unorderedTree_stillOrdersParentBeforeChild() = runBlocking {
        val folders = listOf(
            folder(id = 3L, parentId = 2L, name = "grandchild"),
            folder(id = 1L, parentId = null, name = "root"),
            folder(id = 2L, parentId = 1L, name = "child")
        )

        val plan = FavoriteImportPlanner().plan(folders)

        assertEquals(listOf("root", "child", "grandchild"), plan.nodes.map { it.dto.name })
        assertEquals(null, plan.nodes[0].parentOldId)
        assertEquals(1L, plan.nodes[1].parentOldId)
        assertEquals(2L, plan.nodes[2].parentOldId)
    }

    @Test
    fun brokenCycleAndNullIdFolders_areFlatRoots() = runBlocking {
        val folders = listOf(
            folder(id = 1L, parentId = null, name = "root"),
            folder(id = 2L, parentId = 1L, name = "child"),
            folder(id = 3L, parentId = 99L, name = "broken"),
            folder(id = 4L, parentId = 5L, name = "cycle-a"),
            folder(id = 5L, parentId = 4L, name = "cycle-b"),
            folder(id = null, parentId = 1L, name = "null-id")
        )

        val plan = FavoriteImportPlanner().plan(folders)
        val byName = plan.nodes.associateBy { it.dto.name }

        assertEquals(1L, byName.getValue("child").parentOldId)
        assertNull("缺失父 ID 的节点应挂根", byName.getValue("broken").parentOldId)
        assertNull("环中节点应挂根", byName.getValue("cycle-a").parentOldId)
        assertNull("环中节点应挂根", byName.getValue("cycle-b").parentOldId)
        assertNull("id=null 的 FOLDER 应挂根", byName.getValue("null-id").parentOldId)
    }

    @Test
    fun missingParentSubtree_isImportedFlatAsBefore() = runBlocking {
        val folders = listOf(
            folder(id = 10L, parentId = 99L, name = "broken-parent"),
            folder(id = 11L, parentId = 10L, name = "child"),
            folder(id = 12L, parentId = 11L, name = "grandchild")
        )

        val plan = FavoriteImportPlanner().plan(folders)

        assertEquals(listOf("broken-parent", "child", "grandchild"), plan.nodes.map { it.dto.name })
        assertTrue(
            "无法解析的整棵子树必须按旧兼容语义挂根",
            plan.nodes.all { it.parentOldId == null }
        )
    }

    @Test
    fun selfLoop_isPlannedAsFlatRoot() = runBlocking {
        val plan = FavoriteImportPlanner().plan(
            listOf(folder(id = 7L, parentId = 7L, name = "self"))
        )

        assertEquals(1, plan.nodes.size)
        assertNull(plan.nodes.single().parentOldId)
    }

    @Test
    fun plan_observesCancellationDuringInputTraversal() = runBlocking {
        supervisorScope {
            lateinit var job: Job
            val folders = object : AbstractList<FavoriteDto>() {
                override val size: Int = 100_000

                override fun get(index: Int): FavoriteDto {
                    if (index == 0) job.cancel()
                    return folder(id = index.toLong() + 1L, parentId = null)
                }
            }
            job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                FavoriteImportPlanner().plan(folders)
            }

            job.start()
            job.join()

            assertTrue(job.isCancelled)
        }
    }

    private fun folder(
        id: Long?,
        parentId: Long?,
        name: String = "node-$id"
    ) = FavoriteDto(
        id = id,
        parentId = parentId,
        name = name,
        type = "FOLDER",
        path = "",
        sourceType = "LOCAL",
        createdAt = id ?: 0L
    )
}
