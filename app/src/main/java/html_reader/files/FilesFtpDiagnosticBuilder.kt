package com.html_reader.files

object FilesFtpDiagnosticBuilder {
    fun buildMessage(rawBytes: ByteArray, currentEncoding: String): String {
        val sb = StringBuilder()
        sb.append("Raw Hex:\n")
        sb.append(rawBytes.joinToString(" ") { "%02X".format(it) })
        sb.append("\n\n")
        val charsets = listOf("UTF-8", "GBK", "ISO-8859-1", "Big5", "Shift_JIS", "windows-1251")
        sb.append("Decoding Previews:\n")
        for (csName in charsets) {
            try {
                val decoded = String(rawBytes, java.nio.charset.Charset.forName(csName))
                sb.append("[$csName]: $decoded\n")
            } catch (e: Exception) {
                sb.append("[$csName]: <Error: ${e.message}>\n")
            }
        }
        sb.append("\nCurrent Config: $currentEncoding")
        return sb.toString()
    }
}
