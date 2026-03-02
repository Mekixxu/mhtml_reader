package core.common

/**
 * 统一结果封装，可以直接用 Result<T> 但自定义更可分级传递
 */
sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: AppError) : Outcome<Nothing>()
}

inline fun <T> Outcome<T>.getOrNull(): T? =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> null
    }
