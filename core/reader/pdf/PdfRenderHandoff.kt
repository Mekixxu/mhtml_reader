package core.reader.pdf

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class PdfRenderResourceOwner<T : Any> internal constructor(
    private val release: (T) -> Unit
) {
    private var pending: T? = null

    fun own(resource: T): T {
        check(pending == null) { "A PDF render resource is already pending" }
        pending = resource
        return resource
    }

    internal fun transfer(resource: T) {
        check(pending === resource) { "The returned PDF resource was not registered" }
        pending = null
    }

    internal fun releasePending() {
        val resource = pending
        pending = null
        resource?.let(release)
    }
}

object PdfRenderHandoff {
    /**
     * Keeps a produced resource owned until withContext successfully returns it to the caller.
     */
    suspend fun <T : Any> withResource(
        dispatcher: CoroutineDispatcher,
        release: (T) -> Unit,
        producer: suspend (PdfRenderResourceOwner<T>) -> T
    ): T {
        val owner = PdfRenderResourceOwner(release)
        try {
            val resource = withContext(dispatcher) { producer(owner) }
            owner.transfer(resource)
            return resource
        } finally {
            owner.releasePending()
        }
    }
}
