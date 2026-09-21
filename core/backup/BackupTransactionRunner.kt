package core.backup

/**
 * 备份导入事务执行器。
 *
 * 默认直接执行；App 侧注入 Room `withTransaction` 实现，保证导入中途失败可整体回滚。
 * 注入实现时，参与导入的仓库方法必须避免在事务内切换到其他线程访问数据库
 * （Room 事务是线程约束的），否则会抛
 * "Cannot access database on a different coroutine context inherited from a suspending transaction"。
 */
interface BackupTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T

    object Direct : BackupTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}
