package core.reader.web

/**
 * Coordinates WebView progress sampling with content restoration and Fragment visibility.
 */
class WebProgressSamplingState {
    private var activeGeneration: Long? = null
    private var restoredGeneration: Long? = null
    private var resumed = false

    fun beginContent(generation: Long) {
        activeGeneration = generation
        restoredGeneration = null
    }

    fun onPause() {
        resumed = false
    }

    fun onResume(generation: Long): Boolean {
        resumed = true
        return generation == activeGeneration && restoredGeneration == generation
    }

    fun onRestoreCompleted(generation: Long): Boolean {
        if (generation != activeGeneration) return false
        restoredGeneration = generation
        return resumed
    }
}
