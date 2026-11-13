package com.litelisten.player.ui.browse

import com.google.common.truth.Truth.assertThat
import com.litelisten.player.R
import com.litelisten.player.core.model.SourceType
import org.junit.Test

class ServerFormFactoryTest {

    @Test
    fun jellyfinRequiresBothUsernameAndPassword() {
        val form = ServerForm(host = "192.168.88.51", port = "8096")
        assertThat(ServerFormFactory.validate(SourceType.JELLYFIN, form))
            .isEqualTo(R.string.validation_jellyfin_login)
        assertThat(
            ServerFormFactory.validate(SourceType.JELLYFIN, form.copy(username = "bob")),
        ).isEqualTo(R.string.validation_password_empty)
        assertThat(
            ServerFormFactory.validate(
                SourceType.JELLYFIN,
                form.copy(username = "bob", password = "secret"),
            ),
        ).isNull()
    }

    @Test
    fun smbChecksCredentialsBeforeShareName() {
        val form = ServerForm(host = "192.168.1.2", port = "445")
        assertThat(ServerFormFactory.validate(SourceType.SMB, form))
            .isEqualTo(R.string.validation_username_empty)
        val withLogin = form.copy(username = "u", password = "p")
        assertThat(ServerFormFactory.validate(SourceType.SMB, withLogin))
            .isEqualTo(R.string.validation_share_empty)
        assertThat(ServerFormFactory.validate(SourceType.SMB, withLogin.copy(share = "music")))
            .isNull()
    }

    @Test
    fun webdavWithoutCredentialsAlsoRequiresPassword() {
        val form = ServerForm(url = "http://192.168.1.3:5000/dav")
        assertThat(ServerFormFactory.validate(SourceType.WEBDAV, form))
            .isEqualTo(R.string.validation_username_empty)
    }

    @Test
    fun anonymousServerSkipsCredentials() {
        val form = ServerForm(
            host = "192.168.1.2", port = "445",
            share = "music", anonymous = true,
        )
        assertThat(ServerFormFactory.validate(SourceType.SMB, form)).isNull()
        assertThat(ServerFormFactory.credentials(SourceType.SMB, form)).isNull()
    }

    @Test
    fun jellyfinNeverFallsBackToAnonymous() {
        val form = ServerForm(
            host = "192.168.88.51", port = "8096",
            username = "bob", password = "secret", anonymous = true,
        )
        assertThat(ServerFormFactory.credentials(SourceType.JELLYFIN, form)?.username)
            .isEqualTo("bob")
    }

    @Test
    fun discoveredJellyfinUrlIsSplitIntoHostAndPort() {
        val form = ServerFormFactory.initial(
            SourceType.JELLYFIN, "", "http://192.168.88.51:8096/", null,
        )
        assertThat(form.host).isEqualTo("192.168.88.51")
        assertThat(form.port).isEqualTo("8096")
        assertThat(ServerFormFactory.buildUrl(SourceType.JELLYFIN, form))
            .isEqualTo("http://192.168.88.51:8096/")
    }

    @Test
    fun tlsPortBuildsHttpsUrl() {
        val form = ServerForm(host = "192.168.88.51", port = "8920")
        assertThat(ServerFormFactory.buildUrl(SourceType.JELLYFIN, form))
            .isEqualTo("https://192.168.88.51:8920/")
    }

    @Test
    fun defaultPortsMatchEachProtocol() {
        assertThat(ServerFormFactory.defaultPort(SourceType.SMB)).isEqualTo(445)
        assertThat(ServerFormFactory.defaultPort(SourceType.JELLYFIN)).isEqualTo(8096)
        assertThat(ServerFormFactory.defaultPort(SourceType.WEBDAV)).isEqualTo(80)
    }

    @Test
    fun dlnaDoesNotRequireCredentials() {
        // DLNA/UPnP 是匿名协议：不应要求用户名密码，也不应发送任何凭据。
        val form = ServerForm(url = "http://192.168.1.9:50001/MediaServer/ContentDirectory/Control")
        assertThat(ServerFormFactory.validate(SourceType.DLNA, form)).isNull()
        assertThat(ServerFormFactory.credentials(SourceType.DLNA, form)).isNull()
    }
}
