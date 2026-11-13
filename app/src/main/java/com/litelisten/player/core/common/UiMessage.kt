package com.litelisten.player.core.common

import androidx.annotation.StringRes
import com.litelisten.player.R
import com.litelisten.player.core.model.SourceError

/** SourceError -> 用户可读文案，见《05 契约》§11。 */
object UiMessage {
    @StringRes
    fun of(error: SourceError): Int = when (error) {
        is SourceError.NetworkUnreachable -> R.string.error_network_unreachable
        is SourceError.AuthFailed -> R.string.error_auth_failed
        is SourceError.ShareNotFound -> R.string.error_share_not_found
        is SourceError.PathNotFound -> R.string.error_path_not_found
        is SourceError.Timeout -> R.string.error_timeout
        is SourceError.Protocol -> protocolRes(error.detail)
        is SourceError.Unsupported -> R.string.error_unsupported
        is SourceError.NotFound -> R.string.error_not_found
        is SourceError.Unknown -> R.string.error_unknown
    }

    /**
     * 与 of() 配套的格式参数。以前浏览器把这统一写成空串，
     * 用户看到的是「无法识别的内容（）」，真实原因全被吞掉。
     */
    fun argOf(error: SourceError): String = when (error) {
        is SourceError.NetworkUnreachable -> error.host
        is SourceError.ShareNotFound -> error.share
        is SourceError.PathNotFound -> error.path
        is SourceError.Protocol -> when {
            error.detail.startsWith("HTTP_") -> error.detail.removePrefix("HTTP_")
            error.detail.startsWith("SOAP_") -> error.detail.removePrefix("SOAP_")
            error.detail.startsWith("SMB_STATUS:") -> error.detail.removePrefix("SMB_STATUS:")
            error.detail.startsWith("BROWSE_") -> error.detail.removePrefix("BROWSE_")
            else -> ""
        }
        else -> ""
    }

    /** 把内部协议代号翻译成能指导下一步操作的中文，而不是甩一个空括号。 */
    @StringRes
    private fun protocolRes(detail: String): Int = when {
        detail == "NON_LAN" -> R.string.error_non_lan_host
        detail == "HOST_UNRESOLVED" -> R.string.error_host_unresolved
        detail.startsWith("SMB_STATUS:") -> R.string.error_smb_status
        detail.startsWith("BROWSE_") -> R.string.error_browse_failed
        detail == "MissingHost" || detail == "EMPTY_HOST" -> R.string.error_missing_host
        detail == "NO_CONTROL_URL" -> R.string.error_dlna_no_control
        detail == "NO_DIDL_RESULT" -> R.string.error_dlna_no_result
        detail == "EMPTY_RESPONSE" -> R.string.error_empty_response
        detail == "NOT_WEBDAV" || detail == "NOT_MULTISTATUS" -> R.string.error_not_webdav
        detail == "LIST_SHARES_FAILED" -> R.string.error_shares_unavailable
        detail == "HTTP_401" || detail == "HTTP_403" -> R.string.error_auth_failed
        detail == "HTTP_404" || detail == "HTTP_410" -> R.string.error_path_not_found
        detail.startsWith("HTTP_") || detail.startsWith("SOAP_") -> R.string.error_http_code
        else -> R.string.error_protocol
    }
}
