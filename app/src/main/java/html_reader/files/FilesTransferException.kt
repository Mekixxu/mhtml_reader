package com.html_reader.files

sealed class FilesTransferException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InputUnavailable(cause: Throwable? = null) : FilesTransferException("Input stream unavailable", cause)
    class AuthFailed(cause: Throwable? = null) : FilesTransferException("Authentication failed", cause)
    class ConnectionFailed(cause: Throwable? = null) : FilesTransferException("Connection failed", cause)
    class PermissionDenied(cause: Throwable? = null) : FilesTransferException("Permission denied", cause)
    class Unknown(cause: Throwable? = null) : FilesTransferException(cause?.message ?: "Transfer failed", cause)
}
