package com.litelisten.player.core.model

/** 面向 UI 的统一错误；绝不携带凭据原文。 */
sealed class SourceError(message: String?) : Exception(message) {
    class NetworkUnreachable(val host: String) : SourceError("NETWORK_UNREACHABLE:$host")
    class AuthFailed : SourceError("AUTH_FAILED")
    class ShareNotFound(val share: String) : SourceError("SHARE_NOT_FOUND:$share")
    class PathNotFound(val path: String) : SourceError("PATH_NOT_FOUND:$path")
    class Timeout : SourceError("TIMEOUT")
    class Protocol(val detail: String) : SourceError("PROTOCOL:$detail")
    class NotFound : SourceError("NOT_FOUND")
    class Unsupported(val detail: String) : SourceError("UNSUPPORTED:$detail")
    class Unknown(cause: Throwable?) : SourceError("UNKNOWN:${cause?.javaClass?.simpleName}")
}
