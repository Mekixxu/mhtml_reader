package core.common

/**
 * 全局统一错误模型，所有 VFS / Cache / UseCase 层抛出都使用此模型
 */
sealed class AppError(msg: String? = null, cause: Throwable? = null) : Throwable(msg, cause) {
    object PermissionDenied : AppError("Permission denied")
    object NotFound : AppError("Not found")
    object OutOfSpace : AppError("Out of storage space")
    object InvalidUri : AppError("Invalid URI")
    object UnsupportedOperation : AppError("Unsupported Operation")
    data class IoError(val detail: String? = null, val t: Throwable? = null) : AppError(detail, t)
}
