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
        is SourceError.Protocol -> R.string.error_protocol
        is SourceError.Unsupported -> R.string.error_unsupported
        is SourceError.NotFound -> R.string.error_not_found
        is SourceError.Unknown -> R.string.error_unknown
    }
}
