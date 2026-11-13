package com.litelisten.player.core.common

import com.litelisten.player.R
import com.litelisten.player.core.model.SourceError
import org.junit.Assert.assertEquals
import org.junit.Test

class UiMessageTest {

    @Test
    fun protocolDetailIsKeptAsArgument() {
        assertEquals("500", UiMessage.argOf(SourceError.Protocol("HTTP_500")))
        assertEquals(
            "STATUS_ACCESS_DENIED",
            UiMessage.argOf(SourceError.Protocol("SMB_STATUS:STATUS_ACCESS_DENIED")),
        )
        assertEquals("", UiMessage.argOf(SourceError.Protocol("NO_DIDL_RESULT")))
    }

    @Test
    fun hostIsKeptAsArgument() {
        assertEquals(
            "192.168.1.9",
            UiMessage.argOf(SourceError.NetworkUnreachable("192.168.1.9")),
        )
        assertEquals("Music", UiMessage.argOf(SourceError.ShareNotFound("Music")))
    }

    @Test
    fun protocolCodesMapToActionableMessages() {
        assertEquals(
            R.string.error_host_unresolved,
            UiMessage.of(SourceError.Protocol("HOST_UNRESOLVED")),
        )
        assertEquals(
            R.string.error_not_webdav,
            UiMessage.of(SourceError.Protocol("NOT_WEBDAV")),
        )
        assertEquals(
            R.string.error_http_code,
            UiMessage.of(SourceError.Protocol("HTTP_500")),
        )
        assertEquals(
            R.string.error_smb_status,
            UiMessage.of(SourceError.Protocol("SMB_STATUS:X")),
        )
        assertEquals(
            R.string.error_browse_failed,
            UiMessage.of(SourceError.Protocol("BROWSE_XmlPullParserException")),
        )
    }
}
