package core.reader.web

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object SafeUrlLogSummary {
    fun summarize(url: String): String {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return UNAVAILABLE_TARGET
        } catch (_: IllegalArgumentException) {
            return UNAVAILABLE_TARGET
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return UNAVAILABLE_TARGET
        val host = uri.host?.takeIf(::isSafeHost)?.lowercase(Locale.ROOT)
        return buildString {
            append("scheme=").append(scheme)
            if (host != null) append(" host=").append(host)
        }
    }

    private fun isSafeHost(host: String): Boolean =
        host.length <= MAX_HOST_LENGTH &&
            host.isNotBlank() &&
            host.all { character ->
                character.code < ASCII_LIMIT &&
                    (character.isLetterOrDigit() || character in SAFE_HOST_PUNCTUATION)
            }

    private const val UNAVAILABLE_TARGET = "target=unavailable"
    private const val MAX_HOST_LENGTH = 253
    private const val ASCII_LIMIT = 128
    private const val SAFE_HOST_PUNCTUATION = ".-:[]"
}
